package com.appzgarden.automldemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.util.Log
import android.widget.Toast
import com.google.firebase.ml.common.modeldownload.FirebaseLocalModel
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

private val CAMERA_WRITE_EXTERNAL_REQUEST_CODE: Int = 0x12
private val CAMERA_REQUEST_CODE: Int = 0x16

class MainActivity : AppCompatActivity() {

    private var file: File? = null
    private var currentFileUri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val localModel = FirebaseLocalModel.Builder("model")
            .setAssetFilePath("model/manifest.json")
            .build()
        FirebaseModelManager.getInstance().registerLocalModel(localModel)

        capture_button?.setOnClickListener {
            if (isPermissionGranted()) {
                openCameraIntent(CAMERA_REQUEST_CODE)
            }
        }
    }

    private fun isPermissionGranted(): Boolean {
        var isGranted = true
        if ((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            isGranted = false
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                CAMERA_WRITE_EXTERNAL_REQUEST_CODE
            )
        }
        return isGranted
    }

    private fun openCameraIntent(requestCode: Int) {
        val pictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (pictureIntent.resolveActivity(packageManager!!) != null) {
            try {
                file = createImageFile()
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
            if (file != null) {
                currentFileUri =
                    FileProvider.getUriForFile(this, "com.appzgarden.automldemo.provider", file!!)
                pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentFileUri)
                startActivityForResult(pictureIntent, requestCode)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            try {
                if (requestCode == CAMERA_REQUEST_CODE) {
                    val image: FirebaseVisionImage
                    try {
                        image = FirebaseVisionImage.fromFilePath(this, currentFileUri!!)

                        val labelerOptions = FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder()
                            .setLocalModelName("model")    // Skip to not use a local model
                            .setConfidenceThreshold(0.05f)  // Evaluate your model in the Firebase console
                            // to determine an appropriate value.
                            .build()
                        val labeler = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(labelerOptions)
                        labeler.processImage(image)
                            .addOnSuccessListener { labels ->
                                Log.e("Label",labels.size.toString())
                                for (label in labels) {
                                    val text = label.text
                                    val confidence = label.confidence
                                    Log.e("Text", text)
                                    Log.e("Confidence", confidence.toString())
                                }
                            }
                            .addOnFailureListener { e ->
                              e.printStackTrace()
                            }

                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Something went wrong!! Try again", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG).show()
        }

    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())
        val imageFileName = "IMG_" + timeStamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_WRITE_EXTERNAL_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                openCameraIntent(CAMERA_REQUEST_CODE)
            }
        }
    }
}
