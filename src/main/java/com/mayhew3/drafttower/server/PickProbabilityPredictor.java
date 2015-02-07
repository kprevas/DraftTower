package com.mayhew3.drafttower.server;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mayhew3.drafttower.server.DraftController.DraftStatusListener;
import com.mayhew3.drafttower.shared.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Provides probability of players being selected.
 */
@Singleton
public class PickProbabilityPredictor implements DraftStatusListener {

  private static final Logger logger = Logger.getLogger(DraftControllerImpl.class.getName());

  private final Map<TeamDraftOrder, Map<Long, Float>> predictionsByTeam = new ConcurrentHashMap<>();
  private final PlayerDataSource playerDataSource;
  private final TeamDataSource teamDataSource;
  private final BeanFactory beanFactory;
  private final RosterUtil rosterUtil;
  private final PredictionModel predictionModel;
  private int lastPicksSize;

  @Inject
  public PickProbabilityPredictor(PlayerDataSource playerDataSource,
      TeamDataSource teamDataSource,
      DraftController draftController,
      BeanFactory beanFactory,
      RosterUtil rosterUtil,
      PredictionModel predictionModel) {
    this.playerDataSource = playerDataSource;
    this.teamDataSource = teamDataSource;
    this.beanFactory = beanFactory;
    this.rosterUtil = rosterUtil;
    this.predictionModel = predictionModel;

    for (int i = 1; i <= 10; i++) {
      predictionsByTeam.put(new TeamDraftOrder(i), new HashMap<Long, Float>());
    }

    draftController.addListener(this);
  }

  @Override
  public void onDraftStatusChanged(DraftStatus draftStatus) {
    List<DraftPick> picks = draftStatus.getPicks();
    if (lastPicksSize > picks.size()) {
      return;
    }
    if (picks.size() == lastPicksSize
        && !predictionsByTeam.get(new TeamDraftOrder(1)).isEmpty()) {
      return;
    }
    // When draft status changes, recompute predictions for team that just picked.
    // This gets a little weird with backed out picks but shouldn't do too much damage.
    if (picks.size() - lastPicksSize > 10) {
      lastPicksSize = picks.size() - 10;
    }
    Set<Long> selectedPlayers = new HashSet<>();
    for (int i = 0; i < lastPicksSize; i++) {
      selectedPlayers.add(picks.get(i).getPlayerId());
    }
    for (int pickNum = lastPicksSize;
         pickNum < picks.size() || predictionsByTeam.get(new TeamDraftOrder(1)).isEmpty();
         pickNum++) {
      try {
        if (pickNum < picks.size()) {
          selectedPlayers.add(picks.get(pickNum).getPlayerId());
        }
        int nextTeam = picks.isEmpty() ? 1 : picks.get(pickNum).getTeam() + 1;
        if (nextTeam > 10) {
          nextTeam -= 10;
        }
        int nextPickNum = pickNum + 1;
        logger.info("Updating predictions for team " + nextTeam);
        Map<Position, Integer[]> numFilled = rosterUtil.getNumFilled(picks, pickNum);

        TeamDraftOrder draftOrder = new TeamDraftOrder(nextTeam);
        ListMultimap<Position, Long> topPlayerIds = getTopPlayers(selectedPlayers, draftOrder);

        Map<Long, Float> predictions = predictionsByTeam.get(draftOrder);
        predictions.clear();
        for (Entry<Position, Collection<Long>> entry : topPlayerIds.asMap().entrySet()) {
          Position position = entry.getKey();
          // Safe cast per javadoc of ListMultimap.
          List<Long> topPlayers = (List<Long>) entry.getValue();
          for (int i = 0; i < getNumPlayersForPosition(position); i++) {
            if (i < topPlayers.size()) {
              predictions.put(topPlayers.get(i),
                  predictionModel.getPrediction(position, i, nextPickNum, numFilled.get(position)));
            }
          }
        }
      } catch (DataSourceException e) {
        e.printStackTrace();
      }
    }
    lastPicksSize = picks.size();
  }

  private ListMultimap<Position, Long> getTopPlayers(Set<Long> selectedPlayers, TeamDraftOrder draftOrder) throws DataSourceException {
    TableSpec tableSpec = beanFactory.createTableSpec().as();
    tableSpec.setPlayerDataSet(PlayerDataSet.CBSSPORTS);
    tableSpec.setSortCol(PlayerColumn.DRAFT);
    tableSpec.setAscending(true);
    List<Player> players = playerDataSource.getPlayers(
        teamDataSource.getTeamIdByDraftOrder(draftOrder), tableSpec);
    ListMultimap<Position, Long> topPlayerIds = ArrayListMultimap.create();
    for (Player player : players) {
      if (selectedPlayers.contains(player.getPlayerId())) {
        continue;
      }
      for (String positionStr : RosterUtil.splitEligibilities(player.getEligibility())) {
        Position position = Position.fromShortName(positionStr);
        if (position == Position.DH) {
          continue;
        }
        List<Long> topPositionIds = topPlayerIds.get(position);
        if (topPositionIds.size() < getNumPlayersForPosition(position)) {
          topPositionIds.add(player.getPlayerId());
        }
      }
      boolean filledAllPositions = true;
      for (Entry<Position, Collection<Long>> entry : topPlayerIds.asMap().entrySet()) {
        if (entry.getValue().size() < getNumPlayersForPosition(entry.getKey())) {
          filledAllPositions = false;
          break;
        }
      }
      if (filledAllPositions) {
        break;
      }
    }
    return topPlayerIds;
  }

  private int getNumPlayersForPosition(Position position) {
    return (position == Position.P || position == Position.OF) ? 5 : 3;
  }

  public Map<Long, Float> getTeamPredictions(TeamDraftOrder teamDraftOrder) {
    return predictionsByTeam.get(teamDraftOrder);
  }

  public void reset() {
    lastPicksSize = 0;
    for (TeamDraftOrder team : predictionsByTeam.keySet()) {
      predictionsByTeam.get(team).clear();
    }
  }
}