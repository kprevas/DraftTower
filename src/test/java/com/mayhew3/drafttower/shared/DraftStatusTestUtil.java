package com.mayhew3.drafttower.shared;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mayhew3.drafttower.server.TestPlayerDataSource;

import java.util.HashSet;
import java.util.List;

/**
 * Convenience methods for testing draft status.
 */
public class DraftStatusTestUtil {
  private static int statusSerialId = 0;
  private static int lastPlayerId = 1;

  public static DraftStatus createDraftStatus(List<DraftPick> picks, BeanFactory beanFactory) {
    DraftStatus draftStatus = beanFactory.createDraftStatus().as();
    draftStatus.setPicks(Lists.newArrayList(picks));
    draftStatus.setCurrentPickDeadline(1);
    draftStatus.setCurrentTeam(picks.isEmpty()
        ? 1
        : picks.get(picks.size() - 1).getTeam() + 1);
    draftStatus.setConnectedTeams(new HashSet<Integer>());
    draftStatus.setNextPickKeeperTeams(new HashSet<Integer>());
    draftStatus.setRobotTeams(new HashSet<Integer>());
    draftStatus.setSerialId(statusSerialId++);
    return draftStatus;
  }

  public static DraftPick createDraftPick(int team, String name, boolean isKeeper, BeanFactory beanFactory) {
    return createDraftPick(team, name, isKeeper, "P", lastPlayerId++, beanFactory);
  }

  public static DraftPick createDraftPick(int team, String name, boolean isKeeper, String position, BeanFactory beanFactory) {
    return createDraftPick(team, name, isKeeper, position, lastPlayerId++, beanFactory);
  }

  public static DraftPick createAndPostDraftPick(
      int team, String name, boolean isKeeper, Position position, BeanFactory beanFactory, TestPlayerDataSource playerDataSource) {
    DraftPick draftPick = createDraftPick(team, name, isKeeper, position.getShortName(), playerDataSource.getNextUnclaimedPlayer(position), beanFactory);
    playerDataSource.postDraftPick(draftPick, null);
    return draftPick;
  }

  public static DraftPick createDraftPick(int team, String name, boolean isKeeper, String position, long id, BeanFactory beanFactory) {
    DraftPick draftPick = beanFactory.createDraftPick().as();
    draftPick.setTeam(team);
    draftPick.setEligibilities(ImmutableList.of(position));
    draftPick.setKeeper(isKeeper);
    draftPick.setPlayerName(name);
    draftPick.setPlayerId(id);
    return draftPick;
  }
}