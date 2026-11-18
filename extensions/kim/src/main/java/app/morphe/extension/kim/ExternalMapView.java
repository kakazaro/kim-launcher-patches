package app.morphe.extension.kim;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Shows another app inside the Navigation tab: the app runs on a virtual display drawn into this
 * view, and touches are forwarded to that display.
 * <p>
 * The patch makes this class implement Flutter's PlatformView interface (its name is obfuscated
 * and changes per app version), which is why {@link #getView()} etc. carry no {@code @Override}.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class ExternalMapView extends TextureView implements TextureView.SurfaceTextureListener {
    /** PUBLIC | OWN_CONTENT_ONLY | DESTROY_CONTENT_ON_REMOVAL (same as Kim's old embedded navigation, plus destroy). */
    private static final int FLAGS = 1 | 8 | 256;

    // One display for the whole process: the map app keeps running (and navigating) while the
    // Navigation tab is hidden or Flutter recreates this view.
    private static VirtualDisplay display;
    private static String displayPackage;
    /** null = not tried yet. Direct injection needs INJECT_EVENTS; otherwise use the shell. */
    private static Boolean canInject;

    private final String packageName;
    private Surface surface;
    private float downX, downY;
    private long downTime;

    ExternalMapView(Context context, String packageName) {
        super(context);
        this.packageName = packageName;
        setSurfaceTextureListener(this);
    }

    // region PlatformView
    public View getView() {
        return this;
    }

    public void dispose() {
        detachSurface();
    }

    public void onFlutterViewAttached(View flutterView) {
    }

    public void onFlutterViewDetached() {
    }
    // endregion

    @android.annotation.SuppressLint("WrongConstant") // 256 = DESTROY_CONTENT_ON_REMOVAL, hidden
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        int dpi = getResources().getDisplayMetrics().densityDpi;
        if (display != null && packageName.equals(displayPackage)) {
            display.resize(width, height, dpi);
            display.setSurface(surface);
            return;
        }
        if (display != null) display.release();
        DisplayManager dm = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        display = dm.createVirtualDisplay("morphe-navigation", width, height, dpi, surface, FLAGS);
        displayPackage = packageName;
        launch(display.getDisplay().getDisplayId());
    }

    private void launch(int displayId) {
        Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
        ComponentName component = intent == null ? null : intent.getComponent();
        if (component == null) return;
        // ponytail: force-stop so an already-open task starts on our display instead of being
        // brought to the front on the main screen. Costs a cold start once per Kim process.
        ExternalNav.shellAsync(getContext(), "am force-stop " + ExternalNav.quote(packageName)
                + "; am start --display " + displayId + " -n "
                + ExternalNav.quote(component.flattenToShortString()));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (display != null) {
            display.resize(width, height, getResources().getDisplayMetrics().densityDpi);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        detachSurface();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

    private void detachSurface() {
        if (surface == null) return;
        if (display != null && packageName.equals(displayPackage)) display.setSurface(null);
        surface.release();
        surface = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (display == null) return true;
        int displayId = display.getDisplay().getDisplayId();
        if (inject(event, displayId)) return true;

        // Fallback (what Kim's own embedded navigation did): one tap or swipe per gesture, sent
        // through the shell when the finger lifts. No multi-touch (pinch zoom).
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = event.getEventTime();
                break;
            case MotionEvent.ACTION_UP:
                int x = (int) event.getX(), y = (int) event.getY();
                long duration = Math.max(event.getEventTime() - downTime, 50);
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                boolean tap = Math.hypot(x - downX, y - downY) < slop
                        && duration < ViewConfiguration.getLongPressTimeout();
                ExternalNav.shellAsync(getContext(), tap
                        ? "input -d " + displayId + " tap " + x + " " + y
                        : "input -d " + displayId + " swipe " + (int) downX + " " + (int) downY
                        + " " + x + " " + y + " " + duration);
                break;
        }
        return true;
    }

    private static boolean inject(MotionEvent event, int displayId) {
        if (Boolean.FALSE.equals(canInject)) return false;
        MotionEvent copy = MotionEvent.obtain(event);
        try {
            copy.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            InputEvent.class.getMethod("setDisplayId", int.class).invoke(copy, displayId);
            Object im = InputManager.class.getMethod("getInstance").invoke(null);
            Object ok = InputManager.class.getMethod("injectInputEvent", InputEvent.class, int.class)
                    .invoke(im, copy, 0);
            canInject = Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            canInject = false;
        } finally {
            copy.recycle();
        }
        return canInject;
    }
}
