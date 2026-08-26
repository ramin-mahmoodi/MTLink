package ir.mtlink.client

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

object MTFonts {
    @Volatile private var regular: Typeface? = null
    @Volatile private var semiBold: Typeface? = null

    fun face(context: Context, bold: Boolean): Typeface {
        return if (bold) {
            semiBold ?: ResourcesCompat.getFont(context, R.font.vazirmatn_semibold)!!.also { semiBold = it }
        } else {
            regular ?: ResourcesCompat.getFont(context, R.font.vazirmatn_regular)!!.also { regular = it }
        }
    }
}
