package com.Obscrum.pchwmonitor.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.palette.graphics.Palette
import androidx.palette.graphics.Target
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.FileOutputStream

object CustomBackgroundManager {

    private const val BG_DIR = "custom_backgrounds"
    private const val BG_FILE = "background.jpg"

    fun getBackgroundFile(context: Context): File {
        val dir = File(context.filesDir, BG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, BG_FILE)
    }

    fun saveBackground(context: Context, uri: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()

            val file = getBackgroundFile(context)
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
            fos.close()
            bitmap.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeBackground(context: Context): Boolean {
        val file = getBackgroundFile(context)
        return if (file.exists()) file.delete() else false
    }

    fun loadBitmap(context: Context): Bitmap? {
        val file = getBackgroundFile(context)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun extractColors(bitmap: Bitmap): BackgroundColors {
        val palette = Palette.from(bitmap)
            .addTarget(Target.LIGHT_VIBRANT)
            .addTarget(Target.DARK_VIBRANT)
            .addTarget(Target.VIBRANT)
            .addTarget(Target.MUTED)
            .addTarget(Target.DARK_MUTED)
            .generate()

        val vibrant = palette.vibrantSwatch
        val darkVibrant = palette.darkVibrantSwatch
        val lightVibrant = palette.lightVibrantSwatch
        val muted = palette.mutedSwatch
        val darkMuted = palette.darkMutedSwatch

        val primary = vibrant?.rgb ?: darkVibrant?.rgb ?: muted?.rgb ?: 0xFF6EA8FF.toInt()
        val secondary = lightVibrant?.rgb ?: muted?.rgb ?: vibrant?.rgb ?: 0xFFCCC2DC.toInt()
        val tertiary = darkMuted?.rgb ?: darkVibrant?.rgb ?: muted?.rgb ?: 0xFFEFB8C8.toInt()
        val surface = darkVibrant?.rgb ?: darkMuted?.rgb ?: primary
        val background = darkMuted?.rgb ?: surface

        return BackgroundColors(
            primary = Color(primary),
            secondary = Color(secondary),
            tertiary = Color(tertiary),
            surface = Color(surface).copy(alpha = 0.85f),
            background = Color(background),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onSurface = Color.White,
            onBackground = Color.White,
        )
    }

    fun buildDarkScheme(colors: BackgroundColors) = darkColorScheme(
        primary = colors.primary,
        secondary = colors.secondary,
        tertiary = colors.tertiary,
        surface = colors.surface,
        background = colors.background,
        onPrimary = colors.onPrimary,
        onSecondary = colors.onSecondary,
        onTertiary = colors.onTertiary,
        onSurface = colors.onSurface,
        onBackground = colors.onBackground,
    )

    fun buildLightScheme(colors: BackgroundColors) = lightColorScheme(
        primary = colors.primary,
        secondary = colors.secondary,
        tertiary = colors.tertiary,
        surface = colors.surface,
        background = colors.background,
        onPrimary = colors.onPrimary,
        onSecondary = colors.onSecondary,
        onTertiary = colors.onTertiary,
        onSurface = colors.onSurface,
        onBackground = colors.onBackground,
    )
}

data class BackgroundColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val background: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onTertiary: Color,
    val onSurface: Color,
    val onBackground: Color,
)
