package dev.assay

import android.view.View
import android.widget.TextView

internal fun View.setTextColor(color: Int) {
    require(this is TextView) { "Text styling requires TextView" }
    setTextColor(color)
}

internal var View.textSize: Float
    get() {
        require(this is TextView) { "Text sizing requires TextView" }
        return textSize
    }
    set(value) {
        require(this is TextView) { "Text sizing requires TextView" }
        textSize = value
    }
