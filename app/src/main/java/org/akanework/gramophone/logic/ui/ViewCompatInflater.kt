/*
 *     Copyright (C) 2024 nift4
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.ui

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.TypefaceCompat
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.theme.MaterialComponentsViewInflater
import org.akanework.gramophone.R

@Suppress("UNUSED") // reflection by androidx via theme attr viewInflaterClass
class ViewCompatInflater
@JvmOverloads constructor(impl: ViewCompatInflaterImpl = ViewCompatInflaterImpl()) :
    CallbackViewInflater(impl, impl) {
    class ViewCompatInflaterImpl : MaterialComponentsViewInflater(), Callback {

        override fun createTextView(context: Context, attrs: AttributeSet?): AppCompatTextView {
            return TypefaceCompatTextView(context, attrs)
        }

        class TypefaceCompatTextView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            MaterialTextView(context, attrs, defStyleAttr) {
            constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
            constructor(context: Context) : this(context, null)

            override fun setTextAppearance(resId: Int) {
                super.setTextAppearance(resId)
                val a = if (resId != -1) {
                    context.theme.obtainStyledAttributes(resId, R.styleable.MyTextAppearance)
                } else null
                val fontWeight = a?.getInt(R.styleable.MyTextAppearance_textFontWeight, -1)?.also {
                    a.recycle()
                } ?: -1
                if (fontWeight != -1) {
                    val tf = TypefaceCompat.create(context, typeface, fontWeight, typeface!!.isItalic)
                    setTypeface(tf)
                }
            }

            @Deprecated(
                "Deprecated in Java", ReplaceWith(
                    "super.setTextAppearance(resId)",
                    "android.widget.TextView"
                )
            )
            override fun setTextAppearance(context: Context, resId: Int) {
                super.setTextAppearance(context, resId)
                val a = if (resId != -1) {
                    context.theme.obtainStyledAttributes(resId, R.styleable.MyTextAppearance)
                } else null
                val fontWeight = a?.getInt(R.styleable.MyTextAppearance_textFontWeight, -1)?.also {
                    a.recycle()
                } ?: -1
                if (fontWeight != -1) {
                    val tf = TypefaceCompat.create(context, typeface, fontWeight, typeface!!.isItalic)
                    setTypeface(tf)
                }
            }

        }

        override fun onCreateView(
            context: Context,
            attrs: AttributeSet,
            result: View?
        ) {
            if (result is TextView) {
                // perhaps some descendant, not just TypefaceCompatTextView
                var fontWeight = -1
                val theme = context.theme
                var a = theme.obtainStyledAttributes(
                    attrs,
                    androidx.appcompat.R.styleable.AppCompatTextView,
                    0,
                    0
                )
                var appearance: TypedArray? = null
                val ap = a.getResourceId(
                    androidx.appcompat.R.styleable.AppCompatTextView_android_textAppearance,
                    -1
                )
                a.recycle()
                if (ap != -1) {
                    appearance = theme.obtainStyledAttributes(ap, R.styleable.MyTextAppearance)
                }
                if (appearance != null && appearance.hasValue(R.styleable.MyTextAppearance_textFontWeight)) {
                    fontWeight =
                        appearance.getInt(R.styleable.MyTextAppearance_textFontWeight, -1)
                    appearance.recycle()
                }
                a = theme.obtainStyledAttributes(
                    attrs,
                    R.styleable.MyTextAppearance,
                    0,
                    0
                )
                if (a.hasValue(R.styleable.MyTextAppearance_textFontWeight)) {
                    fontWeight = a.getInt(
                        R.styleable.MyTextAppearance_textFontWeight,
                        -1
                    )
                }
                a.recycle()
                if (fontWeight != -1) {
                    val tf = TypefaceCompat.create(
                        result.context,
                        result.typeface,
                        fontWeight,
                        result.typeface.isItalic
                    )
                    result.setTypeface(tf)
                }
            }
            val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.MyTooltipCompat,
                0,
                0
            )
            val tooltip = a.getText(R.styleable.MyTooltipCompat_tooltipText)
            if (tooltip != null) {
                result?.let { TooltipCompat.setTooltipText(it, tooltip) }
            }
        }
    }
}