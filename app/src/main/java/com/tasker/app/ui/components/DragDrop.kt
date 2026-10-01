package com.tasker.app.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.tasker.app.data.Task
import kotlin.math.roundToInt

/**
 * A small drag & drop engine shared by every view.
 *
 * Items start dragging on long-press. While dragging, the [DragDropHost] tracks the pointer
 * in the initial pass (so lists don't scroll underneath), renders a floating ghost, auto-scrolls
 * registered scroll containers and finally reports the drop target key plus the insertion index
 * among the other items registered under that key.
 */
@Stable
class DragDropState {
    var dragging by mutableStateOf<Task?>(null)
        private set
    var hoverKey by mutableStateOf<String?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var grab by mutableStateOf(Offset.Zero)
        private set
    var itemSize by mutableStateOf(IntSize.Zero)
        private set
    var hostOrigin by mutableStateOf(Offset.Zero)
        internal set

    private var sourceKey: String? = null
    internal var lastPointer = Offset.Zero
    internal val targets = HashMap<String, Rect>()
    internal val items = HashMap<String, Pair<String, Rect>>()
    internal val scrollers = HashMap<Any, Triple<Rect, ScrollableState, Boolean>>()

    /** (taskId, targetKey, orderedIdsInTarget (without dragged), insertIndex) */
    var onDrop: (String, String, List<String>, Int) -> Unit = { _, _, _, _ -> }

    val isDragging get() = dragging != null

    fun start(task: Task, key: String, bounds: Rect) {
        dragging = task
        sourceKey = key
        pointer = lastPointer.takeIf { bounds.contains(it) } ?: bounds.center
        grab = pointer - bounds.topLeft
        itemSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt())
        updateHover()
    }

    internal fun move(p: Offset) {
        pointer = p
        updateHover()
    }

    internal fun end() {
        val t = dragging
        val key = hoverKey
        if (t != null && key != null) {
            val ordered = items.entries
                .filter { it.value.first == key && it.key != t.id }
                .sortedWith(compareBy({ it.value.second.top }, { it.value.second.left }))
            val index = ordered.count { it.value.second.center.y < pointer.y }
            onDrop(t.id, key, ordered.map { it.key }, index)
        }
        cancel()
    }

    fun cancel() {
        dragging = null
        hoverKey = null
        sourceKey = null
    }

    private fun updateHover() {
        hoverKey = targets.entries
            .filter { it.value.contains(pointer) }
            .minByOrNull { it.value.width * it.value.height }
            ?.key
    }
}

@Composable
fun rememberDragDropState(): DragDropState = remember { DragDropState() }

/** Wrap a screen with this to enable dragging. [ghost] renders the floating item. */
@Composable
fun DragDropHost(
    state: DragDropState,
    modifier: Modifier = Modifier,
    ghost: @Composable (Task) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val edge = with(density) { 56f * this.density }
    Box(
        modifier
            .onGloballyPositioned { state.hostOrigin = it.positionInWindow() }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull() ?: continue
                        val p = state.hostOrigin + ch.position
                        state.lastPointer = p
                        if (state.isDragging) {
                            if (ch.pressed) {
                                state.move(p)
                                ev.changes.forEach { it.consume() }
                            } else {
                                ev.changes.forEach { it.consume() }
                                state.end()
                            }
                        }
                    }
                }
            }
    ) {
        content()
        val t = state.dragging
        if (t != null) {
            val o = state.pointer - state.grab - state.hostOrigin
            Box(
                Modifier
                    .zIndex(10f)
                    .offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                    .size(with(density) { state.itemSize.width.toDp() }, with(density) { state.itemSize.height.toDp() })
                    .scale(1.03f)
            ) { ghost(t) }
        }
    }
    // Auto-scroll containers while dragging near their edges
    LaunchedEffect(state.dragging) {
        if (state.dragging == null) return@LaunchedEffect
        while (state.isDragging) {
            withFrameNanos { }
            val p = state.pointer
            state.scrollers.values.forEach { (rect, scroll, vertical) ->
                if (!rect.contains(p)) return@forEach
                val (pos, start, end) = if (vertical) Triple(p.y, rect.top, rect.bottom) else Triple(p.x, rect.left, rect.right)
                val delta = when {
                    pos < start + edge -> -(start + edge - pos) / edge * 22f
                    pos > end - edge -> (pos - (end - edge)) / edge * 22f
                    else -> 0f
                }
                if (delta != 0f) scroll.dispatchRawDelta(delta)
            }
        }
    }
}

/** Marks an area as a drop target. */
fun Modifier.dropTarget(state: DragDropState, key: String): Modifier = composed {
    DisposableEffect(key) { onDispose { state.targets.remove(key) } }
    onGloballyPositioned { state.targets[key] = it.boundsInWindow() }
}

/** Registers a scroll container for auto-scrolling during a drag. */
fun Modifier.dragAutoScroll(state: DragDropState, scroll: ScrollableState, vertical: Boolean = true): Modifier = composed {
    val token = remember { Any() }
    DisposableEffect(token) { onDispose { state.scrollers.remove(token) } }
    onGloballyPositioned { state.scrollers[token] = Triple(it.boundsInWindow(), scroll, vertical) }
}

/** Makes a task draggable (long-press) and clickable. [key] is the drop target it lives in. */
fun Modifier.draggableTask(
    state: DragDropState,
    task: Task,
    key: String,
    onClick: () -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(task.id, key) { onDispose { if (state.items[task.id]?.first == key) state.items.remove(task.id) } }
    this
        .onGloballyPositioned {
            bounds = it.boundsInWindow()
            state.items[task.id] = key to bounds
        }
        .alpha(if (state.dragging?.id == task.id) 0.3f else 1f)
        .combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                state.start(task, key, bounds)
            },
        )
}
