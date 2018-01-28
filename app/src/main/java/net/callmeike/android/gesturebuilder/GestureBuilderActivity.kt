/* $Id: $
   Copyright 2017, G. Blake Meike

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package net.callmeike.android.gesturebuilder

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.gesture.Gesture
import android.gesture.GestureLibraries
import android.gesture.GestureLibrary
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.LongSparseArray
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.gestures_list.*
import java.io.File
import java.lang.ref.SoftReference


internal class NamedGesture(val name: String, val gesture: Gesture, var bitmap: Bitmap? = null) {
    override fun equals(other: Any?): Boolean {
        val id = (other as? NamedGesture)?.gesture?.id ?: return false
        return gesture.id == id
    }
    override fun hashCode(): Int {
        return gesture.id.toInt()
    }
}


internal class GesturesAdapter(private val act: GestureBuilderActivity) : ArrayAdapter<NamedGesture>(act, 0) {
    private val thumbnails = LongSparseArray<Drawable>()

    internal fun addBitmap(id: Long, bitmap: Bitmap?) {
        if (bitmap == null) {
            return
        }
        thumbnails.put(id, BitmapDrawable(act.resources, bitmap))
    }

    override fun getView(pos: Int, cv: View?, root: ViewGroup): View {
        val gesture = getItem(pos)

        val label = (cv ?: LayoutInflater.from(act).inflate(R.layout.gestures_item, root, false)) as TextView
        label.tag = gesture
        label.text = gesture.name
        label.setCompoundDrawablesWithIntrinsicBounds(thumbnails[gesture.gesture.id], null, null, null)

        return label
    }
}


internal class GesturesLoader(_act: GestureBuilderActivity) : AsyncTask<Void, NamedGesture, Int>() {
    private var thumbnailSize: Int = 0
    private var thumbnailInset: Int = 0
    private var pathColor: Int = 0
    private val act = SoftReference(_act)

    override fun onPreExecute() {
        val rez = act.get()?.resources ?: return
        pathColor = rez.getColor(R.color.gesture_color)
        thumbnailInset = rez.getDimension(R.dimen.gesture_thumbnail_inset).toInt()
        thumbnailSize = rez.getDimension(R.dimen.gesture_thumbnail_size).toInt()

        act.get()?.preLoad()
    }

    override fun onProgressUpdate(vararg values: NamedGesture) {
        act.get()?.onUpdate(values[0])
    }

    override fun onPostExecute(result: Int?) {
        act.get()?.onLoadComplete(result)
    }

    override fun doInBackground(vararg params: Void): Int? {
        if (isCancelled) {
            return GestureBuilderActivity.STATUS_CANCELLED
        }

        if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            return GestureBuilderActivity.STATUS_NO_STORAGE
        }

        val store = GestureBuilderActivity.store!!
        if (!store.load()) {
            return GestureBuilderActivity.STATUS_NOT_LOADED
        }

        for (name in store.gestureEntries) {
            for (gesture in store.getGestures(name)) {
                if (isCancelled) {
                    return GestureBuilderActivity.STATUS_CANCELLED
                }

                publishProgress(NamedGesture(
                        name,
                        gesture,
                        gesture.toBitmap(thumbnailSize, thumbnailSize, thumbnailInset, pathColor)))
            }
        }

        return GestureBuilderActivity.STATUS_SUCCESS
    }
}


class GestureBuilderActivity : AppCompatActivity() {
    companion object {
        val STATUS_SUCCESS = 0
        val STATUS_CANCELLED = 1
        val STATUS_NO_STORAGE = 2
        val STATUS_NOT_LOADED = 3

        val MENU_ID_RENAME = 1
        val MENU_ID_REMOVE = 2

        val DIALOG_RENAME_GESTURE = 1

        val REQUEST_NEW_GESTURE = 1

        val GESTURES_INFO_ID = "gestures.info_id"

        internal var store: GestureLibrary? = null
            private set
    }

    private val storeFile = File(Environment.getExternalStorageDirectory(), "gestures")

    private val sorter = Comparator<NamedGesture> { object1, object2 -> object1.name.compareTo(object2.name) }

    private lateinit var gesturesAdapter: GesturesAdapter
    private var task: GesturesLoader? = null

    private var newName: EditText? = null
    private var renameDialog: Dialog? = null
    private var currentRenameGesture: NamedGesture? = null

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)

        menu.setHeaderTitle(((menuInfo as AdapterView.AdapterContextMenuInfo).targetView as TextView).text)

        menu.add(0, MENU_ID_RENAME, 0, R.string.gestures_rename)
        menu.add(0, MENU_ID_REMOVE, 0, R.string.gestures_delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val gesture = menuInfo.targetView.tag as NamedGesture

        when (item.itemId) {
            MENU_ID_RENAME -> {
                renameGesture(gesture)
                return true
            }
            MENU_ID_REMOVE -> {
                deleteGesture(gesture)
                return true
            }
        }

        return super.onContextItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_NEW_GESTURE -> loadGestures()
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.gestures_list)

        gesturesAdapter = GesturesAdapter(this)
        gesture_list.adapter = gesturesAdapter

        if (store == null) {
            store = GestureLibraries.fromFile(storeFile)
        }
        loadGestures()

        registerForContextMenu(gesture_list)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (currentRenameGesture != null) {
            outState.putLong(GESTURES_INFO_ID, currentRenameGesture!!.gesture.id)
        }
    }

    override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)

        val id = state.getLong(GESTURES_INFO_ID, -1)
        if (id == -1L) {
            return
        }

        val entries = store!!.gestureEntries
        for (name in entries) {
            for (gesture in store!!.getGestures(name)) {
                if (gesture.id == id) {
                    currentRenameGesture = NamedGesture(name, gesture)
                    return
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if ((task != null) && (task!!.status != AsyncTask.Status.FINISHED)) {
            task!!.cancel(true)
            task = null
        }

        cleanupRenameDialog()
    }

    override fun onCreateDialog(id: Int, args: Bundle?): Dialog {
        return if (id == DIALOG_RENAME_GESTURE) {
            createRenameDialog()
        } else {
            super.onCreateDialog(id, args)
        }
    }

    override fun onPrepareDialog(id: Int, dialog: Dialog) {
        super.onPrepareDialog(id, dialog)
        if (id == DIALOG_RENAME_GESTURE) {
            newName!!.setText(currentRenameGesture!!.name)
        }
    }

    fun reloadGestures(ignored: View) {
        loadGestures()
    }

    fun addGesture(ignored: View) {
        startActivityForResult(Intent(this, CreateGestureActivity::class.java), REQUEST_NEW_GESTURE)
    }

    internal fun preLoad() {
        add_button.isEnabled = false
        reload_button.isEnabled = false

        gesturesAdapter.setNotifyOnChange(false)
        gesturesAdapter.clear()
    }

    internal fun onLoadComplete(result: Int?) {
        when (result) {
            STATUS_NO_STORAGE -> {
                gesture_list.visibility = View.GONE
                program_status.visibility = View.VISIBLE
                program_status.text = getString(R.string.gestures_error_loading, storeFile.absolutePath)
            }
            else -> {
                gesture_list.visibility = View.VISIBLE
                program_status.visibility = View.GONE
                add_button.isEnabled = true
                reload_button.isEnabled = true
                checkForEmpty()
            }
        }
    }

    internal fun onUpdate(gesture: NamedGesture) {
        if (gesture.bitmap != null) {
            gesturesAdapter.addBitmap(gesture.gesture.id, gesture.bitmap)
        }

        gesturesAdapter.setNotifyOnChange(false)
        gesturesAdapter.add(gesture)
        gesturesAdapter.sort(sorter)
        gesturesAdapter.notifyDataSetChanged()
    }

    private fun loadGestures() {
        if (task != null && task!!.status != AsyncTask.Status.FINISHED) {
            task!!.cancel(true)
        }

        task = GesturesLoader(this)
        task!!.execute()
    }

    private fun checkForEmpty() {
        if (gesturesAdapter.count != 0) {
            return
        }
        program_status.setText(R.string.gestures_empty)
    }

    private fun renameGesture(gesture: NamedGesture) {
        currentRenameGesture = gesture
        showDialog(DIALOG_RENAME_GESTURE)
    }

    private fun createRenameDialog(): Dialog {
        val layout = View.inflate(this, R.layout.dialog_rename, null)

        newName = layout.findViewById(R.id.new_name)

        return AlertDialog.Builder(this)
                .setIcon(0)
                .setTitle(getString(R.string.gestures_rename_title))
                .setCancelable(true)
                .setOnCancelListener({ _ -> cleanupRenameDialog() })
                .setNegativeButton(
                        getString(R.string.cancel_action),
                        { _, _ -> cleanupRenameDialog() })
                .setPositiveButton(getString(R.string.rename_action),
                        { _, _ -> changeGestureName() })
                .setView(layout)
                .create()
    }

    private fun changeGestureName() {
        val name = newName!!.text.toString()
        if (TextUtils.isEmpty(name)) {
            currentRenameGesture = null
            return
        }

        val renameGesture = currentRenameGesture
        currentRenameGesture = null

        val count = gesturesAdapter.count
        if (count <= 0) {
            return
        }

        gesturesAdapter.setNotifyOnChange(false)

        // Simple linear search, there should not be enough items to warrant
        // a more sophisticated search
        for (i in 0 until count) {
            val gesture = gesturesAdapter.getItem(i)
            if (gesture.gesture.id != renameGesture!!.gesture.id) {
                continue
            }
            store!!.removeGesture(gesture.name, gesture.gesture)
            store!!.addGesture(newName!!.text.toString(), gesture.gesture)
            store!!.save()

            gesturesAdapter.remove(gesture)
            gesturesAdapter.add(NamedGesture(newName!!.text.toString(), gesture.gesture, gesture.bitmap))
            gesturesAdapter.sort(sorter)
            break
        }

        gesturesAdapter.notifyDataSetChanged()
    }

    private fun cleanupRenameDialog() {
        if (renameDialog != null) {
            renameDialog!!.dismiss()
            renameDialog = null
        }

        currentRenameGesture = null
    }

    private fun deleteGesture(gesture: NamedGesture) {
        store!!.removeGesture(gesture.name, gesture.gesture)
        store!!.save()

        gesturesAdapter.setNotifyOnChange(false)
        gesturesAdapter.remove(gesture)
        gesturesAdapter.sort(sorter)
        gesturesAdapter.notifyDataSetChanged()
        checkForEmpty()

        Toast.makeText(this, R.string.gestures_delete_success, Toast.LENGTH_SHORT).show()
    }
}
