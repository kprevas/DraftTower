package com.mayhew3.drafttower.client.websocket;

import com.mayhew3.drafttower.shared.SocketTerminationReason;

import java.util.HashSet;
import java.util.Set;

/**
 * Adapted from com.sksamuel.gwt.websockets.
 */
public class Websocket {

  private static int counter = 1;

  public static native boolean isSupported() /*-{
    return ("WebSocket" in window);
  }-*/;

  private final Set<WebsocketListener> listeners = new HashSet<>();

  private final String name;
  private final String url;

  public Websocket(String url) {
    this.url = url;
    this.name = "dtws-" + counter++;
  }

  public native void _close(String s) /*-{
      $wnd.s.close();
  }-*/;

  private native void _open(Websocket ws, String s, String url) /*-{
      $wnd.s = new WebSocket(url);
      $wnd.s.onopen = function () {
        ws.@com.mayhew3.drafttower.client.websocket.Websocket::onOpen()();
      };
      $wnd.s.onclose = function (evt) {
        ws.@com.mayhew3.drafttower.client.websocket.Websocket::onClose(I)(evt.code);
      };
      $wnd.s.onmessage = function (msg) {
        ws.@com.mayhew3.drafttower.client.websocket.Websocket::onMessage(Ljava/lang/String;)(msg.data);
      }
  }-*/;

  public native void _send(String s, String msg) /*-{
      $wnd.s.send(msg);
  }-*/;

  private native int _state(String s) /*-{
      return $wnd.s.readyState;
  }-*/;

  public void addListener(WebsocketListener listener) {
    listeners.add(listener);
  }

  public void close() {
    _close(name);
  }

  public int getState() {
    return _state(name);
  }

  protected void onClose(int closeCode) {
    for (WebsocketListener listener : listeners)
      listener.onClose(SocketTerminationReason.fromCloseCode(closeCode));
  }

  protected void onMessage(String msg) {
    for (WebsocketListener listener : listeners)
      listener.onMessage(msg);
  }

  protected void onOpen() {
    for (WebsocketListener listener : listeners)
      listener.onOpen();
  }

  public void open() {
    _open(this, name, url);
  }

  public void send(String msg) {
    _send(name, msg);
  }
}
