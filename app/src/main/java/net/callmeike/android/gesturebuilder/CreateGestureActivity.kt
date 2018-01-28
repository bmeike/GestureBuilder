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

import android.app.Activity
import android.gesture.Gesture
import android.gesture.GestureOverlayView
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.create_gesture.*
import java.io.File


class CreateGestureActivity : AppCompatActivity(), GestureOverlayView.OnGestureListener {
    companion object {
        private val LENGTH_THRESHOLD = 120.0f
    }

    private var gesture: Gesture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.create_gesture)

        gestures_overlay.addOnGestureListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (gesture != null) {
            outState.putParcelable("gesture", gesture)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        gesture = savedInstanceState.getParcelable("gesture")
        if (gesture != null) {
            gestures_overlay.post { gestures_overlay.gesture = gesture }
            button_done.isEnabled = true
        }
    }

    override fun onGesture(overlay: GestureOverlayView, event: MotionEvent) {}

    override fun onGestureCancelled(overlay: GestureOverlayView, event: MotionEvent) {}

    override fun onGestureStarted(overlay: GestureOverlayView, event: MotionEvent) {
        button_done.isEnabled = false
        gesture = null
    }

    override fun onGestureEnded(overlay: GestureOverlayView, event: MotionEvent) {
        val newGesture = overlay.gesture!!
        if (newGesture.length < LENGTH_THRESHOLD) { overlay.clear(false) }
        button_done.isEnabled = true
        gesture = newGesture
    }

    fun cancelGesture(ignored: View) {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    fun addGesture(ignored: View) {
        if (gesture == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val name = gesture_name.text
        if (name.isEmpty()) {
            gesture_name.error = getString(R.string.error_missing_name)
            return
        }

        val store = GestureBuilderActivity.store!!
        store.addGesture(name.toString(), gesture)
        store.save()

        setResult(Activity.RESULT_OK)

        val path = File(Environment.getExternalStorageDirectory(), "gestures").absolutePath

        Toast.makeText(this, getString(R.string.save_success, path), Toast.LENGTH_SHORT).show()

        finish()
    }
}
