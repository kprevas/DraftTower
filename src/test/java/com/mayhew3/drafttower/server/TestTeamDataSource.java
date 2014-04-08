package com.mayhew3.drafttower.server;

import com.google.inject.Inject;
import com.mayhew3.drafttower.shared.BeanFactory;
import com.mayhew3.drafttower.shared.PlayerDataSet;
import com.mayhew3.drafttower.shared.SharedModule.NumTeams;
import com.mayhew3.drafttower.shared.Team;

import java.util.HashMap;
import java.util.Map;

/**
 * Test version of {@link TeamDataSource}.
 */
public class TestTeamDataSource implements TeamDataSource {

  public static final String BAD_PASSWORD = "badpass";
  public static final int COMMISH_TEAM = 1;

  @Inject BeanFactory beanFactory;
  @Inject @NumTeams int numTeams;

  @Override
  public TeamDraftOrder getTeamDraftOrder(String username, String password) {
    if (password.equals(BAD_PASSWORD)) {
      return null;
    }
    try {
      int teamNumber = Integer.parseInt(username);
      if (teamNumber < 0 || teamNumber > numTeams) {
        return null;
      }
      return new TeamDraftOrder(teamNumber);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public boolean isCommissionerTeam(TeamDraftOrder teamDraftOrder) {
    return teamDraftOrder.get() == COMMISH_TEAM;
  }

  @Override
  public Map<String, Team> getTeams() {
    HashMap<String, Team> teams = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      String teamNumber = Integer.toString(i);
      Team team = beanFactory.createTeam().as();
      team.setShortName(teamNumber);
      team.setLongName(teamNumber);
      teams.put(teamNumber, team);
    }
    return teams;
  }

  @Override
  public HashMap<TeamDraftOrder, PlayerDataSet> getAutoPickWizards() {
    // TODO(kprevas): implement
    return new HashMap<>();
  }

  @Override
  public void updateAutoPickWizard(TeamDraftOrder teamDraftOrder, PlayerDataSet wizardTable) {
    // TODO(kprevas): implement
  }

  @Override
  public TeamDraftOrder getDraftOrderByTeamId(TeamId teamID) {
    return new TeamDraftOrder(teamID.get());
  }

  @Override
  public TeamId getTeamIdByDraftOrder(TeamDraftOrder draftOrder) {
    return new TeamId(draftOrder.get());
  }
}