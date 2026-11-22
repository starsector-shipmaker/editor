#!/usr/bin/env bash

# Starsector Ship Editor - Lifecycle & Process Management Script
# Provides single-instance enforcement, background/foreground running, stop, restart, status, kill, and log monitoring.

set -e

# Force X11 backend for Java AWT to ensure LWJGL's GLX works correctly under Wayland
export GDK_BACKEND=x11
# Only enable _JAVA_AWT_WM_NONREPARENTING on actual non-reparenting WMs or Wayland/XWayland.
# Setting it on reparenting X11 WMs (e.g., Openbox, XFCE/xfwm4, KWin, Mutter) breaks
# AWT screen-coordinate tracking and causes heavyweight JComboBox/JMenu popups to render
# near the top-left of the screen instead of under their dropdown control.
if [ -z "${_JAVA_AWT_WM_NONREPARENTING+x}" ]; then
    wm_hint="$(printf '%s %s' "${XDG_CURRENT_DESKTOP:-}" "${DESKTOP_SESSION:-}" | tr '[:upper:]' '[:lower:]')"
    if [ -n "${WAYLAND_DISPLAY:-}" ] || [ "${XDG_SESSION_TYPE:-}" = "wayland" ]; then
        export _JAVA_AWT_WM_NONREPARENTING=1
    else
        case "$wm_hint" in
            *dwm*|*sway*|*hyprland*|*bspwm*|*awesome*|*xmonad*|*qtile*|*herbstluftwm*|*river*|*wayfire*|*ratpoison*)
                export _JAVA_AWT_WM_NONREPARENTING=1
                ;;
        esac
    fi
fi

# Resolve directory containing ship_editor.jar
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/ship_editor.jar" ]; then
    APP_DIR="$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/../../ship_editor.jar" ]; then
    APP_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
else
    APP_DIR="$SCRIPT_DIR"
fi

cd "$APP_DIR"

PID_FILE="$APP_DIR/.ship_editor.pid"
LOG_DIR="$APP_DIR/logs"
LOG_FILE="$LOG_DIR/ship_editor.log"
LAUNCH_LOG="$LOG_DIR/ship_editor_launcher.log"

JVM_OPTS="-Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 -Dsun.java2d.opengl=false -Dsun.java2d.d3d=false -Dsun.java2d.noddraw=true -Dsun.awt.noerasebackground=true -Dorg.lwjgl.opengl.contextAPI=native"

# Locate JRE
find_java() {
    JAVA_CMD="java"
    for jre_path in "jre/bin/java" "../jre/bin/java" "../../jre/bin/java" "../jre_linux/bin/java" "../../jre_linux/bin/java" "../jre_mac/bin/java" "../../jre_mac/bin/java"; do
        if [ -x "$jre_path" ] || [ -f "$jre_path" ]; then
            JAVA_CMD="$jre_path"
            echo "Found local JRE: $jre_path" >&2
            return 0
        fi
    done
    return 1
}

# Get running PIDs matching ship_editor.jar
get_running_pids() {
    local pids=""
    
    # Check PID file first
    if [ -f "$PID_FILE" ]; then
        local file_pid
        file_pid=$(cat "$PID_FILE" 2>/dev/null || true)
        if [ -n "$file_pid" ] && kill -0 "$file_pid" 2>/dev/null; then
            if ps -p "$file_pid" -o args= 2>/dev/null | grep -qE "ship_editor\.jar|shipeditor"; then
                pids="$file_pid"
            fi
        fi
    fi

    # Also search process table for any unrecorded/orphaned ship_editor instances
    local pgrep_pids
    pgrep_pids=$(pgrep -f "ship_editor\.jar" 2>/dev/null || true)
    for p in $pgrep_pids; do
        if [ "$p" != "$$" ] && [ "$p" != "$PPID" ]; then
            # Verify it's actually Java running ship_editor.jar
            if ps -p "$p" -o args= 2>/dev/null | grep -qE "java.*ship_editor\.jar"; then
                if ! echo "$pids" | grep -qw "$p"; then
                    pids="${pids:+$pids }$p"
                fi
            fi
        fi
    done

    echo "$pids"
}

# Format RSS KB into human readable string
format_rss() {
    local rss_kb="$1"
    if [ -z "$rss_kb" ] || ! [ "$rss_kb" -eq "$rss_kb" ] 2>/dev/null; then
        echo "N/A"
        return
    fi
    if [ "$rss_kb" -ge 1048576 ]; then
        awk "BEGIN {printf \"%.2f GB\", $rss_kb/1048576}"
    elif [ "$rss_kb" -ge 1024 ]; then
        awk "BEGIN {printf \"%.1f MB\", $rss_kb/1024}"
    else
        echo "${rss_kb} KB"
    fi
}

cmd_status() {
    local pids
    pids=$(get_running_pids)
    if [ -z "$pids" ]; then
        echo "Starsector Ship Editor is NOT running."
        if [ -f "$PID_FILE" ]; then
            rm -f "$PID_FILE"
        fi
        return 1
    fi

    echo "Starsector Ship Editor is RUNNING (PID: $pids):"
    printf "%-8s %-10s %-8s %-8s %-12s\n" "PID" "ELAPSED" "%CPU" "%MEM" "RSS MEM"
    printf "%-8s %-10s %-8s %-8s %-12s\n" "--------" "----------" "--------" "--------" "------------"
    for pid in $pids; do
        local info
        info=$(ps -p "$pid" -o pid=,etime=,%cpu=,%mem=,rss= 2>/dev/null || true)
        if [ -n "$info" ]; then
            local p etime cpu mem rss
            read -r p etime cpu mem rss <<< "$info"
            local rss_human
            rss_human=$(format_rss "$rss")
            printf "%-8s %-10s %-8s %-8s %-12s\n" "$p" "$etime" "$cpu%" "$mem%" "$rss_human"
        fi
    done
    return 0
}

cmd_stop() {
    local pids
    pids=$(get_running_pids)
    if [ -z "$pids" ]; then
        echo "Starsector Ship Editor is not running."
        rm -f "$PID_FILE"
        return 0
    fi

    echo "Stopping Starsector Ship Editor (PID: $pids)..."
    for pid in $pids; do
        kill "$pid" 2>/dev/null || true
    done

    # Wait up to 5 seconds for graceful shutdown
    local waited=0
    while [ $waited -lt 5 ]; do
        pids=$(get_running_pids)
        if [ -z "$pids" ]; then
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done

    pids=$(get_running_pids)
    if [ -n "$pids" ]; then
        echo "Process did not exit gracefully after 5s. Force killing (PID: $pids)..."
        for pid in $pids; do
            kill -9 "$pid" 2>/dev/null || true
        done
        sleep 1
    fi

    rm -f "$PID_FILE"
    echo "Starsector Ship Editor stopped."
    return 0
}

cmd_kill() {
    local pids
    pids=$(get_running_pids)
    if [ -z "$pids" ]; then
        echo "Starsector Ship Editor is not running."
        rm -f "$PID_FILE"
        return 0
    fi

    echo "Force killing Starsector Ship Editor (PID: $pids)..."
    for pid in $pids; do
        kill -9 "$pid" 2>/dev/null || true
    done
    rm -f "$PID_FILE"
    echo "Done."
    return 0
}

cmd_logs() {
    mkdir -p "$LOG_DIR"
    local target_log="$LOG_FILE"
    if [ ! -f "$target_log" ] && [ -f "$LAUNCH_LOG" ]; then
        target_log="$LAUNCH_LOG"
    fi

    if [ ! -f "$target_log" ]; then
        echo "No log file found in $LOG_DIR"
        return 1
    fi

    if [ "$1" = "-f" ] || [ "$1" = "--follow" ]; then
        tail -n 50 -f "$target_log"
    else
        tail -n 50 "$target_log"
    fi
}

cmd_start() {
    local background=false
    local force=false
    local extra_args=()

    while [ $# -gt 0 ]; do
        case "$1" in
            -d|--bg|--background|bg)
                background=true
                shift
                ;;
            -f|--force)
                force=true
                shift
                ;;
            *)
                extra_args+=("$1")
                shift
                ;;
        esac
    done

    local existing_pids
    existing_pids=$(get_running_pids)
    if [ -n "$existing_pids" ]; then
        if [ "$force" = false ]; then
            echo "[ERROR] Starsector Ship Editor is already running (PID: $existing_pids)!" >&2
            echo "Use './ship_editor.sh status' to check, './ship_editor.sh restart' to restart, or pass '-f' to force start." >&2
            return 1
        else
            echo "[WARNING] Starting new instance despite running PID: $existing_pids (forced)." >&2
        fi
    fi

    mkdir -p "$LOG_DIR"
    find_java || echo "Local JRE not found. Launching with system Java..." >&2

    if [ "$background" = true ]; then
        echo "Starting Starsector Ship Editor in background..."
        nohup "$JAVA_CMD" $JVM_OPTS -jar ./ship_editor.jar "${extra_args[@]}" > "$LAUNCH_LOG" 2>&1 &
        local new_pid=$!
        echo "$new_pid" > "$PID_FILE"
        echo "Started Starsector Ship Editor with PID $new_pid."
        echo "Launcher output redirected to: $LAUNCH_LOG"
        echo "Use './ship_editor.sh status' or './ship_editor.sh logs' to monitor."
    else
        # In foreground mode, run Java and handle PID cleanup on exit
        echo "$$" > "$PID_FILE"
        trap 'rm -f "$PID_FILE"' EXIT INT TERM
        exec "$JAVA_CMD" $JVM_OPTS -jar ./ship_editor.jar "${extra_args[@]}"
    fi
}

cmd_restart() {
    cmd_stop
    echo "Starting Starsector Ship Editor..."
    cmd_start "$@"
}

show_help() {
    echo "Usage: ./ship_editor.sh [command] [options]"
    echo ""
    echo "Commands:"
    echo "  start [options]     Start the ship editor in foreground (default action if omitted)"
    echo "  bg, start -d, --bg  Start the ship editor as a detached background process"
    echo "  stop                Gracefully stop running ship editor instance(s)"
    echo "  restart [options]   Stop running instance(s) and start anew"
    echo "  status              Check if the ship editor is running, showing PID & resource usage"
    echo "  kill                Force kill (SIGKILL) any stuck or unresponsive instances"
    echo "  logs [-f]           Display recent log output (pass -f to follow)"
    echo "  --cli [args...]     Run the ship editor in CLI mode"
    echo "  help, -h, --help    Show this help message"
    echo ""
    echo "Options for start / restart:"
    echo "  -d, --bg            Run in background"
    echo "  -f, --force         Bypass single-instance check"
}

# Main command dispatch
case "$1" in
    status)
        cmd_status
        ;;
    stop)
        cmd_stop
        ;;
    kill)
        cmd_kill
        ;;
    restart)
        shift
        cmd_restart "$@"
        ;;
    logs|log)
        shift
        cmd_logs "$@"
        ;;
    bg|--bg|--background)
        cmd_start -d
        ;;
    --cli)
        shift
        find_java || true
        exec "$JAVA_CMD" $JVM_OPTS -cp ship_editor.jar shipeditor.CliMain "$@"
        ;;
    help|-h|--help)
        show_help
        ;;
    start)
        shift
        cmd_start "$@"
        ;;
    *)
        cmd_start "$@"
        ;;
esac