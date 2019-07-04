package com.lzp.decals

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt


/**
 *  贴纸的包装器，子View自动获得可以拖动，旋转，缩放等功能
 * */
class DecalsEditWrapper @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {

        private const val TAG = "DecalsEditWrapper"

        private const val RESET = -1
        private const val MOVE = 0
        private const val ROTATE = 1
        private const val SCALE = 2
        private const val FINGER_SCALE = 4
        private const val DELETE = 3

        /**
         *  空白的bitmap，为了判断缓存是否是有效的
         * */
        private var weakReference: WeakReference<Bitmap>? = null

    }

    var deleteDrawable: Drawable = context.getDrawable(R.drawable.icon_shanchu)!!
        set(value) {
            field = value
            invalidate()
        }

    var rotateDrawable: Drawable = context.getDrawable(R.drawable.icon_xuanzhuan)!!
        set(value) {
            field = value
            invalidate()
        }

    var scaleDrawable: Drawable = context.getDrawable(R.drawable.icon_lashen)!!
        set(value) {
            field = value
            invalidate()
        }

    /**
     *  删除，旋转，缩放的按钮的位置
     * */
    private val deleteRectF = RectF()
    private val rotateRectF = RectF()
    private val scaleRectF = RectF()

    /**
     *  删除，旋转，缩放的按钮大小
     * */
    var btnSize = 70f
        set(value) {
            field = value
            val padding = (value / 2).toInt()
            setPadding(padding, padding, padding, padding)
            invalidate()
        }

    /**
     *  边线的宽度
     * */
    var borderWidth = 4f
        set(value) {
            field = value
            paint.strokeWidth = value
            invalidate()
        }

    /**
     * 边线的颜色
     * */
    var borderColor = Color.parseColor("#ffffff")
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    /**
     *  边线的画笔
     * */
    private val paint = Paint()

    /**
     *  当前的模式
     * */
    private var currentMode = RESET

    /**
     *  最小宽或高
     * */
    private var minSize = btnSize * 2

    /**
     * 宽高比，用来等比缩放
     * */
    private var scale: Float = 1f

    /**
     *  用于修正判断旋转的绝对坐标
     *
     *  因为要绝对坐标，一定要减去容器的left，否则旋转会出现问题
     * */
    var offsetX: Int = 0

    /**
     *  用于修正判断旋转的绝对坐标
     *
     *  因为要绝对坐标，一定要减去容器的top，否则旋转会出现问题
     * */
    var offsetY: Int = 0

    private val touchSlop = ViewConfiguration.get(getContext()).scaledTouchSlop

    var listener: DecalsEditWrapperListener? = null


    init {
        isFocusable = true
        isFocusableInTouchMode = true
        paint.isAntiAlias = true
        paint.isDither = true
        paint.color = borderColor
        paint.strokeWidth = borderWidth
        paint.style = Paint.Style.STROKE

        // 解析自定义属性
        if (attrs != null) {
            parseAttributes(attrs)
        }
        deleteDrawable.setBounds(0, 0, btnSize.toInt(), btnSize.toInt())
        rotateDrawable.setBounds(0, 0, btnSize.toInt(), btnSize.toInt())
        scaleDrawable.setBounds(0, 0, btnSize.toInt(), btnSize.toInt())
        val padding = (btnSize / 2).toInt()
        setPadding(padding, padding, padding, padding)

        setWillNotDraw(false)
        // 获取焦点
        requestFocus()
    }

    /**
     *  解析自定义属性
     * */
    private fun parseAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DecalsEditWrapper)
        borderWidth = typedArray.getDimensionPixelSize(R.styleable.DecalsEditWrapper_borderWidth, 4).toFloat()
        borderColor = typedArray.getColor(R.styleable.DecalsEditWrapper_borderColor, Color.parseColor("#ffffff"))
        btnSize = typedArray.getDimensionPixelSize(R.styleable.DecalsEditWrapper_btnSize, 70).toFloat()
        offsetX = typedArray.getDimensionPixelSize(R.styleable.DecalsEditWrapper_offsetX, 0)
        offsetY = typedArray.getDimensionPixelSize(R.styleable.DecalsEditWrapper_offsetY, 0)
        typedArray.getDrawable(R.styleable.DecalsEditWrapper_deleteDrawable)?.let {
            deleteDrawable = it
        }
        typedArray.getDrawable(R.styleable.DecalsEditWrapper_rotateDrawable)?.let {
            rotateDrawable = it
        }
        typedArray.getDrawable(R.styleable.DecalsEditWrapper_scaleDrawable)?.let {
            scaleDrawable = it
        }
        typedArray.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val resultWidthMeasureSpec = if (measuredWidth < minSize) {
            MeasureSpec.makeMeasureSpec(minSize.toInt(), MeasureSpec.getMode(widthMeasureSpec))
        } else {
            widthMeasureSpec
        }
        val resultHeightMeasureSpec = if (measuredHeight < minSize) {
            MeasureSpec.makeMeasureSpec(minSize.toInt(), MeasureSpec.getMode(heightMeasureSpec))
        } else {
            heightMeasureSpec
        }
        setMeasuredDimension(resultWidthMeasureSpec, resultHeightMeasureSpec)

        scale = measuredWidth.toFloat() / measuredHeight
        // 计算三个按钮的位置
        deleteRectF.set(0f, 0f, btnSize, btnSize)
        rotateRectF.set(measuredWidth - btnSize, 0f, measuredWidth.toFloat(), btnSize)
        scaleRectF.set(
            measuredWidth - btnSize,
            measuredHeight - btnSize,
            measuredWidth.toFloat(),
            measuredHeight.toFloat()
        )

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 只有获取到焦点，才绘制出按钮和边线
        if (isFocused) {
            // 先画出四条边线
            drawBorder(canvas)
            // 画出按钮
            drawButton(canvas)
        }
    }

    private fun drawBorder(canvas: Canvas) {
        val halfBtnSize = btnSize / 2
        // 上
        canvas.drawLine(btnSize, halfBtnSize, width - btnSize, halfBtnSize, paint)
        // 下
        canvas.drawLine(
            halfBtnSize - borderWidth / 2,
            height - halfBtnSize,
            width - halfBtnSize,
            height - halfBtnSize,
            paint
        )
        // 左
        canvas.drawLine(halfBtnSize, btnSize, halfBtnSize, height - halfBtnSize, paint)
        // 右
        canvas.drawLine(width - halfBtnSize, btnSize, width - halfBtnSize, height - btnSize, paint)
    }

    private fun drawButton(canvas: Canvas) {
        canvas.save()
        deleteDrawable.draw(canvas)
        canvas.translate(width - btnSize, 0f)
        rotateDrawable.draw(canvas)
        canvas.translate(0f, height - btnSize)
        scaleDrawable.draw(canvas)
        canvas.restore()
    }


    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    private var xDown = 0f

    private var yDown = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            // 手指按下
            MotionEvent.ACTION_DOWN -> {
                // 判断是否在按钮之中
                xDown = event.rawX
                yDown = event.rawY
                //如果没有在按钮上，操作模式为移动模式
                if (!isInMenuButton(event.x, event.y)) {
                    currentMode = MOVE
                }
            }
            MotionEvent.ACTION_MOVE -> {
//                Log.e(TAG, "${event.rawX}: ${event.rawY}")
                // 根据当前的操作模式做不同处理
                when (currentMode) {
                    MOVE -> {
                        // 手指放下 ，拿到触摸点的个数
                        // 如果没有点击按钮或者在移动状态中，才会认为是手势缩放
                        if (event.pointerCount == 2) {
                            currentMode = FINGER_SCALE
                            performFingerScaleByMove(event)
                        } else {
                            currentMode = MOVE
                            lastFingerDistance = 0f
                            performMove(event.rawX - xDown, event.rawY - yDown)
                        }
                    }
                    ROTATE -> performOnRotate(event.rawX, event.rawY)
                    SCALE -> performOnScale(event.rawX - xDown, event.rawY - yDown)
                    // 因为手指从两个到一个，view可能会直接移动到剩余手指的位置，所以如果已经是手势缩放，就不会再回到move状态
                    FINGER_SCALE -> {
                        if (event.pointerCount == 2) {
                            performFingerScaleByMove(event)
                        } else {
                            lastFingerDistance = 0f
                        }
                    }
                }
                xDown = event.rawX
                yDown = event.rawY
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val xUp = event.rawX
                val yUp = event.rawY
                // 如果是一个点击事件
                if (abs(xUp - xDown) < touchSlop && abs(yUp - yDown) < touchSlop) {
                    //点击了删除按钮
                    if (currentMode == DELETE) {
                        performOnDelete()
                    }
                    performOnClick()
                }
                currentMode = RESET
                lastFingerDistance = 0f
            }
        }


        return true
    }

    /**
     *  判断按钮是否点了按钮
     * */
    private fun isInMenuButton(xDown: Float, yDown: Float): Boolean {
        // 是否点击了删除按钮
        if (deleteRectF.contains(xDown, yDown)) {
            currentMode = DELETE
            return true
        }

        // 是否点击了旋转按钮
        if (rotateRectF.contains(xDown, yDown)) {
            currentMode = ROTATE
            return true
        }

        // 是否点击了缩放按钮
        if (scaleRectF.contains(xDown, yDown)) {
            currentMode = SCALE
            return true
        }

        return false
    }

    /**
     *  移动
     * */
    private fun performMove(xDistance: Float, yDistance: Float) {
        translationX += xDistance
        translationY += yDistance
    }

    /**
     *  记录上一次两次手指之间的长度
     * */
    private var lastFingerDistance = 0f

    /**
     *  手势缩放
     * */
    private fun performFingerScaleByMove(event: MotionEvent) {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        val value = sqrt((x * x + y * y).toDouble()).toFloat()// 计算两点的距离
        if (lastFingerDistance != 0f) {
            // 两次手指之间的移动的比例
            val scale = value / lastFingerDistance - 1
            val params = layoutParams as MarginLayoutParams
            val widthScale = params.width * scale
            val heightScale = params.height * scale
            scaleFromCenter(params, widthScale, heightScale, scale)
        }
        lastFingerDistance = value
    }

    private fun scaleFromCenter(
        params: MarginLayoutParams,
        widthAdd: Float,
        heightAdd: Float,
        scale: Float
    ) {
        // 设置新的宽度
        var widthAddResult = widthAdd
        var heightAddResult = heightAdd
        var newWidth = params.width + widthAddResult
        var newHeight = params.height + heightAddResult
        if (scale < 1) {
            // 检查最小宽高
            if (newWidth < minSize) {
                newWidth = minSize
                newHeight = minSize * scale

                widthAddResult = newWidth - params.width
                heightAddResult = newHeight - params.height
            }
        } else {
            // 检查最小宽高
            if (newHeight < minSize) {
                newHeight = minSize
                newWidth = minSize * scale

                heightAddResult = newHeight - params.height
                widthAddResult = newWidth - params.width
            }
        }
        if (params.width != newWidth.toInt() || params.height != newHeight.toInt()) {
            listener?.onScale(this, params.width, params.height, newWidth, newHeight)
            params.width = newWidth.toInt()
            params.height = newHeight.toInt()
            layoutParams = params
            // 要由中心缩放，所以要调整translation
            translationX -= widthAddResult / 2
            translationY -= heightAddResult / 2
        }

    }

    /**
     * 缩放， 不能设置scale，因为Scale会把按钮变小，所以一定要改变自身的大小
     * */
    private fun performOnScale(xDistance: Float, yDistance: Float) {
        autoWireBitmapCacheForTextView(getChildAt(0))
        val params = layoutParams as MarginLayoutParams
        if (scale < 1) {
            scaleFromCenter(params, xDistance, xDistance * scale, scale)
        } else {
            scaleFromCenter(params, yDistance, yDistance * scale, scale)
        }
    }

    /**
     * 旋转
     *  */
    private fun performOnRotate(rawX: Float, rawY: Float) {
        autoWireBitmapCacheForTextView(getChildAt(0))
        // 中心点
        val centerX = width / 2 + translationX + offsetX
        // 加上绝对坐标的偏移值，修正旋转的中心点的误差
        val centerY = height / 2 + translationY + offsetY

        val a = calculatePointLength(centerX, centerY, xDown, yDown)
        val b = calculatePointLength(xDown, yDown, rawX, rawY)
        val c = calculatePointLength(centerX, centerY, rawX, rawY)

        var cosb = (a * a + c * c - b * b) / (2 * a * c)

        if (cosb >= 1) {
            cosb = 1.0
        }

        val radian = acos(cosb)
        var newDegree = Math.toDegrees(radian)

//        center -> proMove的向量， 我们使用PointF来实现
        val centerToProMoveX = xDown - centerX
        val centerToProMoveY = yDown - centerY
//
//        center -> curMove 的向量
        val centerToCurMoveX = rawX - centerX
        val centerToCurMoveY = rawY - centerY

//        向量叉乘结果, 如果结果为负数， 表示为逆时针， 结果为正数表示顺时针
        val result = centerToProMoveX * centerToCurMoveY - centerToProMoveY * centerToCurMoveX

        if (result < 0) {
            newDegree = -newDegree
        }
        rotation = (rotation + newDegree).toFloat()
        listener?.onRotate(this, (rotation + newDegree).toFloat())
    }

    private fun calculatePointLength(startX: Float, startY: Float, stopX: Float, stopY: Float): Double {
        return hypot(startX - stopX.toDouble(), startY - stopY.toDouble())
    }


    /**
     *  点击了删除按钮
     * */
    private fun performOnDelete() {
        listener?.onClickDelete(this)
    }

    /**
     *  点击了按钮
     * */
    private fun performOnClick() {
        requestFocus()
    }

    /**
     *  为TextView设置自己的缓存背景，解决emoji表情不能旋转的问题
     * */
    private fun autoWireBitmapCacheForTextView(child: View) {
        if (child !is TextView) {
            return
        }
        if (child.background == null) {
            child.buildDrawingCache()
            val bitmap = child.drawingCache
            if (weakReference == null || weakReference!!.get() == null) {
                val emptyBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
                weakReference = WeakReference(emptyBitmap)
            }
            if (weakReference!!.get()!!.sameAs(bitmap)) {
                return
            }
            child.text = ""
            child.background = BitmapDrawable(resources, bitmap)
        }
    }

    /**
     *  计算点与点之间的距离
     * */

    interface DecalsEditWrapperListener {

        fun onClickDelete(view: DecalsEditWrapper)

        fun onRotate(view: DecalsEditWrapper, degree: Float)

        fun onScale(view: DecalsEditWrapper, oldWidth: Int, oldHeight: Int, newWidth: Float, newHeight: Float)

    }


}