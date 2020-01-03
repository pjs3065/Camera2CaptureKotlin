package com.example.cameraex

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore.Images.ImageColumns.ORIENTATION
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val tagName = MainActivity::class.java.simpleName

    private var cameraDevice: CameraDevice? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var manager: CameraManager? = null

    //카메라 설정에 관한 멤버 변수
    private var mPreviewSize: Size? = null
    private var map: StreamConfigurationMap? = null

    //권한 멤버 변수
    private val requestCode: Int = 200
    private val permissionArray: Array<String> =
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)


    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {

        //TextureView 생성될시 Available 메소드가 호출된다.
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            // cameraManager 생성
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false

    }

    //카메라 연결 상태 콜백
    private val mStateCallBack = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //CameraDevice 객체 생성
            cameraDevice = camera

            //CaptureRequest.Builder 객체와 CaptureSession 객체 생성하여 미래보기 화면을 실행
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {}
        override fun onError(camera: CameraDevice, error: Int) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //권한 체크하기
        if (checkPermission()) {
            initLayout()
        } else {
            ActivityCompat.requestPermissions(this, permissionArray, requestCode)
        }

        ib_camera.setOnClickListener {
            takePicture()
        }
    }

    /**
     * 권한 체크하기
     */
    private fun checkPermission(): Boolean {
        //권한 요청
        return !(ContextCompat.checkSelfPermission(
            this,
            permissionArray[0]
        ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    permissionArray[1]
                ) != PackageManager.PERMISSION_GRANTED)
    }

    /**
     * 권한 요청에 관한 callback 메소드 구현
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == this.requestCode && grantResults.isNotEmpty()) {
            var permissionGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    //사용자가 권한을 거절했을 시
                    permissionGranted = false
                    break
                }
            }

            //권한을 모두 수락했을 경우
            if (permissionGranted) {
                initLayout()
            } else {
                //권한을 수락하지 않았을 경우
                ActivityCompat.requestPermissions(this, permissionArray, requestCode)
            }
        }
    }

    /**
     * 레이아웃 전개하기
     */
    private fun initLayout() {
        setContentView(R.layout.activity_main)
        preview.surfaceTextureListener = mSurfaceTextureListener
    }

    /**
     * CameraManager 생성
     * 카메라에 관한 정보 얻기
     * openCamera() 메소드 호출 -> CameraDevice 객체 생성
     */
    private fun openCamera(width: Int, height: Int) {
        //카메라 매니저를 생성한다.
        manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        //기본 카메라를 선택한다.
        val cameraId = manager!!.cameraIdList[0]

        //카메라 특성을 가져오기
        val characteristics: CameraCharacteristics =
            manager!!.getCameraCharacteristics(cameraId)
        val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val fps =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        Log.d(tagName, "최대 프레임 비율 : ${fps[fps.size - 1]} hardware level : $level")

        //StreamConfigurationMap 객체에는 카메라의 각종 지원 정보가 담겨져있다.
        map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        //미리보기용 textureView 화면 크기를 설정한다. (제공할 수 있는 최대 크기)
        mPreviewSize = map!!.getOutputSizes(SurfaceTexture::class.java)[0]
        val fpsForVideo = map!!.highSpeedVideoFpsRanges

        Log.e(
            tagName,
            "for video ${fpsForVideo[fpsForVideo.size - 1]} preview Size width: ${mPreviewSize!!.width} height : $height"
        )

        //권한 체크
        if (checkPermission()) {
            //CameraDevice 생
            manager!!.openCamera(cameraId, mStateCallBack, null)
        } else {
            ActivityCompat.requestPermissions(this, permissionArray, requestCode)
        }
    }

    /**
     * Preview 시작
     */
    private fun startPreview() {
        if (cameraDevice == null || !preview.isAvailable || mPreviewSize == null) {
            Log.e(tagName, "startPreview() fail, return")
            return
        }

        val texture = preview.surfaceTexture
        val surface = Surface(texture)

        mPreviewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        mPreviewBuilder!!.addTarget(surface)

        cameraDevice!!.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    mPreviewSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            null
        )
    }

    /**
     * 업데이트 Preview
     */
    private fun updatePreview() {
        cameraDevice?.let {
            mPreviewBuilder!!.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            val thread = HandlerThread("CameraPreview")
            thread.start()

            val backgroundHandler = Handler(thread.looper)
            mPreviewSession!!.setRepeatingRequest(
                mPreviewBuilder!!.build(),
                null,
                backgroundHandler
            )
        }
    }

    /**
     * 사진 캡처
     */
    private fun takePicture() {
        var jpegSizes: Array<Size>? = map?.getOutputSizes(ImageFormat.JPEG)

        var width = 640
        var height = 480

        if (jpegSizes != null && jpegSizes.isNotEmpty()) {
            width = jpegSizes[0].width
            height = jpegSizes[1].height
        }

        val imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
        val outputSurfaces = mutableListOf<Surface>()
        outputSurfaces.add(imageReader.surface)
        outputSurfaces.add(Surface(preview.surfaceTexture))

        val captureBuilder =
            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureBuilder.addTarget(imageReader.surface)

        //이미지가 캡처되는 순간에 제대로 사진 이미지가 나타나도록 3A를 자동으로 설정한다.
        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        val rotation = windowManager.defaultDisplay.rotation
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270)

        val file = File(Environment.getExternalStorageDirectory(), "pic.jpg")

        // 이미지를 캡처할 때 자동으로 호출된다.
        val readerListener = object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader?) {
                imageReader?.let {
                    var image: Image? = null
                    image = imageReader.acquireLatestImage()
                    val buffer: ByteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    save(bytes)
                }
            }

            private fun save(bytes: ByteArray) {
                val output: OutputStream? = FileOutputStream(file)
                output?.let {
                    it.write(bytes)
                    output.close()
                }
            }
        }

        //이미지를 캡처하는 작업은 메인 스레드가 아닌 스레드 핸들러로 수행한다.
        val thread = HandlerThread("CameraPicture")
        thread.start()
        val backgroundHandler = Handler(thread.looper)

        // imageReader 와 ImageReader.OnImageAvailableListener 객체를 서로 연결시키기 위해 설정한다.
        imageReader.setOnImageAvailableListener(readerListener, backgroundHandler)

        val captureCallBack = object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                Toast.makeText(this@MainActivity, "사진이 캡처되었습니다.", Toast.LENGTH_SHORT).show()
                startPreview()
            }
        }

        //사진 이미지를 캡처하는데 사용하는 CameraCaptureSession 생성한다.
        // 이미 존재하면 기존 세션은 자동으로 종료
        cameraDevice!!.createCaptureSession(
            outputSurfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.capture(captureBuilder.build(), captureCallBack, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {

                }
            },
            backgroundHandler
        )
    }
}
