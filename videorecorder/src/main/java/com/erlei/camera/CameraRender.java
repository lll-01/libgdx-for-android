package com.erlei.camera;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;

import com.erlei.gdx.widget.EGLCore;
import com.erlei.gdx.widget.GLContext;
import com.erlei.gdx.widget.IRenderView;
import com.erlei.gdx.graphics.GL20;
import com.erlei.gdx.graphics.Mesh;
import com.erlei.gdx.graphics.Pixmap;
import com.erlei.gdx.graphics.Texture;
import com.erlei.gdx.graphics.VertexAttribute;
import com.erlei.gdx.graphics.VertexAttributes;
import com.erlei.gdx.graphics.glutils.FrameBuffer;
import com.erlei.gdx.graphics.glutils.ShaderProgram;
import com.erlei.gdx.math.Matrix4;
import com.erlei.gdx.utils.Logger;

/**
 * Created by lll on 2018/10/8
 * Email : lllemail@foxmail.com
 * Describe : 使用相机的数据作为纹理渲染到renderView
 */
public class CameraRender extends GLContext implements SurfaceTexture.OnFrameAvailableListener {
    private Logger mLogger = new Logger("CameraRender", Logger.DEBUG);
    private final CameraControl mControl;
    private CameraTexture mCameraTexture;
    private ShaderProgram mProgram;
    private float[] mTexMatrix = new float[16];
    private Matrix4 mMatrix4 = new Matrix4();
    private Matrix4 mProjectionViewMatrix = new Matrix4();
    private Mesh mMesh;
    private Renderer mRenderer;
    private CameraTexture.CameraTextureData mCameraTextureData;
    private FrameBuffer mFrameBuffer;

    public CameraRender(IRenderView renderView, CameraControl cameraControl) {
        super(renderView);
        mControl = cameraControl;
    }

    public CameraRender(IRenderView renderView, CameraControl cameraControl, Renderer renderer) {
        this(renderView, cameraControl);
        mRenderer = renderer;
    }

    public void setRenderer(Renderer renderer) {
        mRenderer = renderer;
    }

    public Renderer getRenderer() {
        return mRenderer;
    }


    @Override
    public void create(EGLCore egl, GL20 gl) {
        super.create(egl, gl);
        mLogger.debug("create");
        initShaderProgram();
        initMesh();
        openCamera();
        if (mRenderer != null) initFrameBuffer();
        if (mRenderer != null) mRenderer.create(egl,gl);
    }

    protected void initFrameBuffer() {
        Size previewSize = mControl.getPreviewSize();
        mFrameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, previewSize.width, previewSize.height, false);
    }

    protected void openCamera() {
        initSurfaceTexture();
        mControl.open(mCameraTexture.getSurfaceTexture());
        Size cameraSize = mControl.getPreviewSize();
        adjustTextureSize(new Size(getWidth(), getHeight()), cameraSize);
        mCameraTextureData.setTextureSize(cameraSize);
    }


    private void closeCamera() {
        mControl.close();
        destroySurfaceTexture();
    }

    @Override
    public void resume() {
        super.resume();
        mLogger.debug("resume");
        openCamera();
        if (mRenderer != null) mRenderer.resume();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        mLogger.debug("resize " + width + "x" + height);

        Size viewSize = new Size(width, height);
        Size cameraSize = mControl.getPreviewSize();
        adjustTextureSize(viewSize, cameraSize);
        if (mRenderer != null) mRenderer.resize(viewSize, cameraSize);
    }

    /**
     * 调整纹理大小比例
     *
     * @param viewSize   视图大小
     * @param cameraSize 相机大小
     */
    private void adjustTextureSize(Size viewSize, Size cameraSize) {
        if (!isLandscape()) cameraSize = new Size(cameraSize.getHeight(), cameraSize.getWidth());
        mProjectionViewMatrix.idt();
        float cameraWidth = cameraSize.width;
        float viewWidth = viewSize.width;
        float cameraHeight = cameraSize.height;
        float viewHeight = viewSize.height;

        //1 . 恢复纹理比例
        mProjectionViewMatrix.scale(cameraWidth / viewWidth, cameraHeight / viewHeight, 0F);

        //2 . CENTER_CROP (see ImageView CENTER_CROP)
        float scale;
        float dx = 0, dy = 0;
        if (cameraWidth * viewHeight > viewWidth * cameraHeight) {
            scale = viewHeight / cameraHeight;
            dx = (viewWidth - cameraWidth * scale) * 0.5f;
        } else {
            scale = viewWidth / cameraWidth;
            dy = (viewHeight - cameraHeight * scale) * 0.5f;
        }
        mLogger.debug("viewSize = " + viewSize.toString() + "\t\t cameraSize = " + cameraSize.toString() + "\t\tscale = " + scale + "\t\t dx = " + (dx / cameraWidth) + "\t\t dy = " + (dy / cameraHeight));
        //mProjectionViewMatrix.translate(dx,dy,0);
        mProjectionViewMatrix.scale(scale, scale, 0f);
    }

    @Override
    public void pause() {
        super.pause();
        mLogger.debug("pause");
        closeCamera();
        if (mRenderer != null) mRenderer.pause();
    }


    @Override
    public void render(GL20 gl) {
        super.render(gl);
        clear();
        mCameraTexture.getSurfaceTexture().updateTexImage();
        mCameraTexture.getSurfaceTexture().getTransformMatrix(mTexMatrix);

        if (mRenderer != null) mFrameBuffer.begin();
        mCameraTexture.bind();
        mProgram.begin();
        mProgram.setUniformMatrix("u_texMatrix", mMatrix4.set(mTexMatrix));
        mProgram.setUniformMatrix("u_projectionViewMatrix", mProjectionViewMatrix);
        mProgram.setUniformi("u_texture", 0);
        mMesh.render(mProgram, GL20.GL_TRIANGLE_FAN);
        mProgram.end();
        if (mRenderer != null) mFrameBuffer.end();

        if (mRenderer != null) mRenderer.render(gl,mFrameBuffer);
    }

    @Override
    public void dispose() {
        if (mRenderer != null) mRenderer.dispose();
        mLogger.debug("dispose");
        destroySurfaceTexture();
        mControl.close();
        mProgram.dispose();
        mMesh.dispose();
        if (mRenderer != null) mFrameBuffer.dispose();
        super.dispose();
    }

    protected void initSurfaceTexture() {
        destroySurfaceTexture();
        mCameraTextureData = new CameraTexture.CameraTextureData();
        mCameraTexture = new CameraTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureData);
        mCameraTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        mCameraTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        mCameraTexture.getSurfaceTexture().setOnFrameAvailableListener(this);
    }

    protected void destroySurfaceTexture() {
        if (mCameraTexture != null) mCameraTexture.dispose();
        mCameraTexture = null;
    }

    protected void initShaderProgram() {
        mProgram = new ShaderProgram(files.internal("shader/vertex_shader.glsl"), files.internal("shader/fragment_shader_oes.glsl"));
    }

    protected void initMesh() {
        float[] vertices = {
                -1f, -1f, // Position 0
                0.0f, 0.0f, // TexCoord 0
                1f, -1f, // Position 1
                1f, 0.0f, // TexCoord 1
                1f, 1f, // Position 2
                1f, 1f, // TexCoord 2
                -1f, 1f, // Position 3
                0.0f, 1f // TexCoord 3
        };

        mMesh = new Mesh(true, 4, 0,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
        mMesh.setVertices(vertices);

    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mRenderView.requestRender();
    }

    public interface CameraControl {

        Size getPreviewSize();

        void open(SurfaceTexture surfaceTexture);

        void close();

    }

    public interface Renderer {

        void create(EGLCore egl, GL20 gl);

        void resize(Size viewSize, Size cameraSize);

        void render(GL20 gl, FrameBuffer frameBuffer);

        void pause();

        void resume();

        void dispose();
    }
}