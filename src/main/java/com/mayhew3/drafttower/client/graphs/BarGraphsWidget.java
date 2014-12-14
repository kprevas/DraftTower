package com.mayhew3.drafttower.client.graphs;

import com.google.common.collect.ImmutableMap;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.AttachEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.mayhew3.drafttower.shared.PlayerColumn;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static com.mayhew3.drafttower.shared.PlayerColumn.*;

/**
 * Widget containing team comparison bar graphs.
 */
public class BarGraphsWidget extends Composite implements BarGraphsView {

  interface Resources extends ClientBundle {
    interface Css extends CssResource {
      String me();
      String avg();
      String graph();
    }

    @Source("BarGraphsWidget.css")
    Css css();
  }

  private static final Resources.Css CSS = ((Resources) GWT.create(Resources.class)).css();
  static {
    CSS.ensureInjected();
  }

  private static final Map<PlayerColumn, Float> MAX_VALUES = ImmutableMap.<PlayerColumn, Float>builder()
      .put(HR, 400f)
      .put(RBI, 1000f)
      .put(OBP, 0.5f)
      .put(SLG, 0.6f)
      .put(RHR, 800f)
      .put(SBCS, 150f)
      .put(INN, 1800f)
      .put(K, 1500f)
      .put(ERA, 4.5f)
      .put(WHIP, 1.4f)
      .put(WL, 50f)
      .put(S, 120f)
      .build();

  private final BarGraphsApi api;

  private final FlowPanel container;
  private final Map<PlayerColumn, Widget> barGraphs = new HashMap<>();

  private boolean apiLoaded;

  @Inject
  public BarGraphsWidget(final BarGraphsPresenter presenter,
      final BarGraphsApi api) {
    this.api = api;
    container = new FlowPanel();
    container.setSize("820px", "750px");
    addLabels();

    api.loadVisualizationApi(new Runnable() {
      @Override
      public void run() {
        apiLoaded = true;
      }
    });

    addAttachHandler(new AttachEvent.Handler() {
      @Override
      public void onAttachOrDetach(AttachEvent event) {
        presenter.setActive(true);
      }
    });

    initWidget(container);
    presenter.setView(this);
  }

  private void addLabels() {
    FlowPanel labels = new FlowPanel();
    Label me = new Label("Me");
    me.setStyleName(CSS.me());
    labels.add(me);
    Label avg = new Label("Avg");
    avg.setStyleName(CSS.avg());
    labels.add(avg);
    container.add(labels);
  }

  @Override
  public void clear() {
    if (apiLoaded) {
      container.clear();
      barGraphs.clear();
      addLabels();
    }
  }

  @Override
  public void updateBar(PlayerColumn statColumn,
      Float myValue, Float avgValue) {
    Widget barGraph = api.createBarGraph(
        statColumn.getLongName(),
        new String[] {"Me", "Avg"},
        new float[] {
            myValue == null ? 0 : myValue,
            avgValue == null ? 0 : avgValue,
        },
        MAX_VALUES.get(statColumn));
    barGraph.addStyleName(CSS.graph());
    container.add(barGraph);
    barGraphs.put(statColumn, barGraph);
  }

  @Override
  protected void onEnsureDebugId(String baseID) {
    super.onEnsureDebugId(baseID);
    for (Entry<PlayerColumn, Widget> barGraph : barGraphs.entrySet()) {
      barGraph.getValue().ensureDebugId(baseID + "-" + barGraph.getKey().getShortName());
    }
  }
}