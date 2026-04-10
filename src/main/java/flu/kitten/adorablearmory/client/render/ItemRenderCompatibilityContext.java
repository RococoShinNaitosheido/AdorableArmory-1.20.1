package flu.kitten.adorablearmory.client.render;

import javax.annotation.Nullable;

public final class ItemRenderCompatibilityContext {

    private enum Phase {
        NORMAL,
        OUTLINE_CAPTURE
    }

    private static final class State {
        private int renderDepth;
        private boolean outlineRenderedThisCall;
        @Nullable private Integer glintColor;
        private Phase phase = Phase.NORMAL;
    }

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private static State getOrCreateState() {
        State state = STATE.get();
        if (state == null) {
            state = new State();
            STATE.set(state);
        }
        return state;
    }

    @Nullable
    private static State getState() {
        return STATE.get();
    }

    public static void beginItemRender() {
        State state = getOrCreateState();
        if (state.renderDepth == 0) {
            state.outlineRenderedThisCall = false;
            state.glintColor = null;
            state.phase = Phase.NORMAL;
        }
        state.renderDepth++;
    }

    public static void setGlintColor(@Nullable Integer argb) {
        State state = getState();
        if (state == null || state.renderDepth <= 0) {
            return;
        }
        state.glintColor = argb;
    }

    @Nullable
    public static Integer currentGlintColor() {
        State state = getState();
        if (state == null || state.renderDepth <= 0) {
            return null;
        }
        if (state.phase != Phase.NORMAL) {
            return null;
        }
        return state.glintColor;
    }

    public static boolean tryStartOutlineCapture() {
        State state = getState();
        if (state == null || state.renderDepth <= 0) {
            return false;
        }
        if (state.outlineRenderedThisCall) {
            return false;
        }

        state.outlineRenderedThisCall = true;
        state.phase = Phase.OUTLINE_CAPTURE;
        return true;
    }

    public static void finishOutlineCapture() {
        State state = getState();
        if (state == null || state.renderDepth <= 0) {
            return;
        }
        state.phase = Phase.NORMAL;
    }

    public static void endItemRender() {
        State state = getState();
        if (state == null) {
            return;
        }

        state.renderDepth--;
        if (state.renderDepth <= 0) {
            STATE.remove();
            return;
        }

        state.phase = Phase.NORMAL;
    }

    private ItemRenderCompatibilityContext() {}
}
