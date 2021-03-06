package com.mayhew3.drafttower.client.events;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.mayhew3.drafttower.client.events.ReloadWindowEvent.Handler;

/**
 * Event fired when window needs to reload.
 */
public class ReloadWindowEvent extends GwtEvent<Handler> {

  public interface Handler extends EventHandler {
    void onReload(ReloadWindowEvent event);
  }

  public static final Type<Handler> TYPE = new Type<>();

  @Override
  public Type<Handler> getAssociatedType() {
    return TYPE;
  }

  @Override
  protected void dispatch(Handler handler) {
    handler.onReload(this);
  }
}