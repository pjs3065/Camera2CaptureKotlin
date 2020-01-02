package com.example.cameraex

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*

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

        try {
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


        } catch (e: CameraAccessException) {
            Log.e(tagName, "openCamera() : 카메라 디바이스에 정상적인 접근이 안됩니다.")
        }
    }

    /**
     * Preview 시작
     */
    private fun startPreview(){
        if(cameraDevice == null || !preview.isAvailable || mPreviewSize == null){
            Log.e(tagName,"startPreview() fail, return")
            return
        }

        val texture = preview.surfaceTexture
        val surface = Surface(texture)
        try{

        }catch (e: CameraAccessException){

        }


    }
}
