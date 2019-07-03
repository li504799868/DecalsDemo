package com.example.decalsdemo

import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lzp.decals.DecalsEditWrapper
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), DecalsEditWrapper.DecalsEditWrapperListener {

    private lateinit var emptyBitmap: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        add_decals.setOnClickListener {
            val view = DecalsEditWrapper(this)
            view.listener = this
            val imageView = TextView(this@MainActivity)
//            imageView.setImageResource(R.drawable.ic_launcher_foreground)
            imageView.setText(getString(), TextView.BufferType.NORMAL);
            imageView.setTextColor(resources.getColor(R.color.accent_material_dark))
            imageView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.default_gap_136).toFloat()
            )

            imageView.gravity = Gravity.CENTER
            view.addView(imageView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val size = resources.getDimensionPixelSize(R.dimen.default_gap_220)
            decals_container.addView(view, size, size)
        }
    }

    fun getString(): String {
        val temp = Character.toChars(0x1F604)
        return String(temp)
//        return "1"
    }

    override fun onClickDelete(view: DecalsEditWrapper) {
        Toast.makeText(this, "点击了删除按钮", Toast.LENGTH_SHORT).show()
        decals_container.removeView(view)
    }

    override fun onScale(view: DecalsEditWrapper, oldWidth: Int, oldHeight: Int, newWidth: Float, newHeight: Float) {
        val child = view.getChildAt(0)
        if (child is TextView) {
            val scale = newWidth / oldWidth
            child.setTextSize(TypedValue.COMPLEX_UNIT_PX, child.textSize * scale)
        }
    }

    override fun onRotate(view: DecalsEditWrapper, degree: Float) {
//        val child = view.getChildAt(0)
////        if (child.background == null) {
//            child.buildDrawingCache()
//            val bitmap = child.drawingCache
//            if (!this::emptyBitmap.isInitialized) {
//                emptyBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
//            }
//            if (emptyBitmap.sameAs(bitmap)) {
//                return
//            }
//            child.background = BitmapDrawable(resources, bitmap)
//
//            Log.e("lzp", "!2121212")
////        }
////        view.getChildAt(0).rotation = degree
    }


}
