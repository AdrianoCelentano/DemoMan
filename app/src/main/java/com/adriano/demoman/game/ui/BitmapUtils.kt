package com.adriano.demoman.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

fun getResizedBitmap(context: Context, resId: Int, widthDp: Int, heightDp: Int): BitmapDescriptor {
    val resources = context.resources
    val widthPx = (widthDp * resources.displayMetrics.density).toInt()
    val heightPx = (heightDp * resources.displayMetrics.density).toInt()

    val bitmap = BitmapFactory.decodeResource(resources, resId)
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, widthPx, heightPx, false)

    return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
}
