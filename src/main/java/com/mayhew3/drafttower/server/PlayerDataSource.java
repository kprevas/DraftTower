package com.mayhew3.drafttower.server;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mayhew3.drafttower.server.ServerModule.TeamTokens;
import com.mayhew3.drafttower.shared.*;
import com.mayhew3.drafttower.shared.SharedModule.NumTeams;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static com.mayhew3.drafttower.shared.PlayerColumn.*;
import static com.mayhew3.drafttower.shared.Position.UNF;

/**
 * Looks up players in the database.
 */
@Singleton
public class PlayerDataSource {

  private static final Logger logger = Logger.getLogger(PlayerDataSource.class.getName());

  private final DataSource db;
  private final BeanFactory beanFactory;
  private final DraftStatus draftStatus;
  private final Map<String, Integer> teamTokens;
  private int numTeams;

  @Inject
  public PlayerDataSource(DataSource db,
      BeanFactory beanFactory,
      DraftStatus draftStatus,
      @TeamTokens Map<String, Integer> teamTokens,
      @NumTeams int numTeams) {
    this.db = db;
    this.beanFactory = beanFactory;
    this.draftStatus = draftStatus;
    this.teamTokens = teamTokens;
    this.numTeams = numTeams;
  }

  public UnclaimedPlayerListResponse lookupUnclaimedPlayers(UnclaimedPlayerListRequest request)
      throws ServletException {
    UnclaimedPlayerListResponse response = beanFactory.createUnclaimedPlayerListResponse().as();

    Integer team = teamTokens.get(request.getTeamToken());

    List<Player> players = Lists.newArrayList();

    ResultSet resultSet = null;
    try {
      resultSet = getResultSetForUnclaimedPlayerRows(request.getRowCount(), request.getRowStart(),
          request.getPositionFilter(),
          request.getTableSpec().getSortCol(), request.getTableSpec().isAscending(),
          team);
      while (resultSet.next()) {
        Player player = beanFactory.createPlayer().as();
        player.setPlayerId(resultSet.getInt("PlayerID"));
        ImmutableMap.Builder<PlayerColumn, String> columnMap = ImmutableMap.builder();

        PlayerColumn[] playerColumns = PlayerColumn.values();
        for (PlayerColumn playerColumn : playerColumns) {
          String columnString = resultSet.getString(playerColumn.getColumnName());
          if (columnString != null) {
            columnMap.put(playerColumn, columnString);
          }
        }

        player.setColumnValues(columnMap.build());

        // TODO(m3)
        if (!request.getHideInjuries() && player.getPlayerId() % 5 == 0) {
          player.setInjury("busted wang");
        }

        players.add(player);
      }
    } catch (SQLException e) {
      throw new ServletException("Error getting next element of results.", e);
    } finally {
      try {
        close(resultSet);
      } catch (SQLException e) {
        throw new ServletException("Error closing DB resources.", e);
      }
    }

    response.setPlayers(players);
    response.setTotalPlayers(getTotalUnclaimedPlayerCount());

    return response;
  }

  public ListMultimap<Integer, Integer> getAllKeepers() throws ServletException {
    ListMultimap<Integer, Integer> keepers = ArrayListMultimap.create();

    String sql = "select TeamID, PlayerID from Keepers";

    ResultSet resultSet = null;
    try {
      resultSet = executeQuery(sql);
      while (resultSet.next()) {
        int teamID = resultSet.getInt("TeamID");
        int playerID = resultSet.getInt("PlayerID");
        keepers.put(teamID, playerID);
      }
    } catch (SQLException e) {
      throw new ServletException("Error retreiving keepers from database.", e);
    } finally {
      try {
        close(resultSet);
      } catch (SQLException e) {
        throw new ServletException("Error closing DB resources.", e);
      }
    }
    return keepers;
  }

  private int getTotalUnclaimedPlayerCount() throws ServletException {
    String sql = "select count(1) as TotalPlayers " +
        "from UnclaimedDisplayPlayersWithCatsByQuality";

    ResultSet resultSet = null;
    try {
      resultSet = executeQuery(sql);
      resultSet.next();
      return resultSet.getInt("TotalPlayers");
    } catch (SQLException e) {
      throw new ServletException("Couldn't find number of rows in table.", e);
    } finally {
      try {
        close(resultSet);
      } catch (SQLException e) {
        throw new ServletException("Error closing DB resources.", e);
      }
    }
  }

  private ResultSet getResultSetForUnclaimedPlayerRows(int rowCount, int rowStart,
      Position positionFilter, PlayerColumn sortCol, boolean ascending, final int team)
      throws SQLException {

    String sql = "select * " +
        "from UnclaimedDisplayPlayersWithCatsByQuality ";

    if (positionFilter != null) {
      if (positionFilter == UNF) {
        ArrayList<DraftPick> picks = Lists.newArrayList(draftStatus.getPicks());
        Set<Position> openPositions = RosterUtil.getOpenPositions(
            Lists.newArrayList(Iterables.filter(picks,
                new Predicate<DraftPick>() {
                  @Override
                  public boolean apply(DraftPick input) {
                    return input.getTeam() == team;
                  }
                })));
        sql += " where Position in (";
        sql += Joiner.on(',').join(Iterables.transform(openPositions, new Function<Position, String>() {
          @Override
          public String apply(Position input) {
            return "'" + input.getShortName() + "'";
          }
        }));
        sql += ") ";
      } else {
        sql += " where Position = '" + positionFilter.getShortName() + "' ";
      }
    }

    if (sortCol != null) {
      sql += "order by case when " + sortCol.getColumnName() + " is null then 1 else 0 end, "
          + sortCol.getColumnName() + " " + (ascending ? "asc " : "desc ");
    } else {
      sql += "order by total desc ";
    }
    sql += "limit " + rowStart + ", " + rowCount;

    return executeQuery(sql);
  }

  public void populateQueueEntry(QueueEntry queueEntry) throws SQLException {
    String sql = "select Player,Eligibility " +
        "from UnclaimedDisplayPlayersWithCatsByQuality " +
        "where PlayerID = " + queueEntry.getPlayerId();

    ResultSet resultSet = executeQuery(sql);
    try {
      resultSet.next();
      queueEntry.setPlayerName(resultSet.getString("Player"));
      queueEntry.setEligibilities(
          splitEligibilities(resultSet.getString("Eligibility")));
    } finally {
      close(resultSet);
    }
  }

  public void populateDraftPick(DraftPick draftPick) throws SQLException {
    String sql = "select FirstName,LastName,Eligibility " +
        "from AllPlayers " +
        "where ID = " + draftPick.getPlayerId();

    ResultSet resultSet = executeQuery(sql);
    try {
      resultSet.next();
      draftPick.setPlayerName(
          resultSet.getString("FirstName") + " " + resultSet.getString("LastName"));
      draftPick.setEligibilities(
          splitEligibilities(resultSet.getString("Eligibility")));
    } finally {
      close(resultSet);
    }
  }

  public long getBestPlayerId(TableSpec tableSpec,
      Set<Position> openPositions) throws SQLException {
    // TODO(m3): use tableSpec.
    String sql = "select PlayerID,Eligibility " +
        "from UnclaimedDisplayPlayersWithCatsByQuality";

    ResultSet resultSet = null;
    try {
      resultSet = executeQuery(sql);
      Long firstReserve = null;
      while (resultSet.next()) {
        if (firstReserve == null) {
          firstReserve = resultSet.getLong("PlayerID");
        }
        List<String> eligibility = splitEligibilities(resultSet.getString("Eligibility"));
        for (String position : eligibility) {
          if (openPositions.contains(Position.fromShortName(position))) {
            return resultSet.getLong("PlayerID");
          }
        }
      }
      return firstReserve;
    } finally {
      close(resultSet);
    }
  }

  public void changePlayerRank(ChangePlayerRankRequest request) {
    // TODO(m3)
    logger.info("Change player rank for team " + teamTokens.get(request.getTeamToken())
        + " player " + request.getPlayerId() + " new rank " + request.getNewRank());
  }

  public void postDraftPick(DraftPick draftPick, DraftStatus status) throws SQLException {
    int overallPick = status.getPicks().size();
    int round = (overallPick - 1) / numTeams + 1;
    int pick = ((overallPick-1) % numTeams) + 1;

    long playerID = draftPick.getPlayerId();
    int teamID = draftPick.getTeam();

    String draftPosition = "'" + draftPick.getEligibilities().get(0) + "'";
    String sql = "INSERT INTO DraftResults (Round, Pick, PlayerID, BackedOut, OverallPick, TeamID, DraftPos, Keeper) " +
        "VALUES (" + round + ", " + pick + ", " + playerID + ", 0, " + overallPick + ", " + teamID +
          ", " + draftPosition + ", " + (draftPick.isKeeper() ? "1" : "0") + ")";

    Statement statement = null;
    try {
      statement = executeUpdate(sql);
    } finally {
      close(statement);
    }
  }

  public void populateDraftStatus(DraftStatus status) throws SQLException {
    String sql = "SELECT * from DraftResultsLoad "
        + "ORDER BY Round, Pick";
    ResultSet resultSet = executeQuery(sql);
    try {
      while (resultSet.next()) {
        DraftPick pick = beanFactory.createDraftPick().as();
        pick.setPlayerId(resultSet.getInt("PlayerID"));
        pick.setPlayerName(resultSet.getString("PlayerName"));
        pick.setEligibilities(
            splitEligibilities(resultSet.getString("Eligibility")));
        pick.setTeam(resultSet.getInt("DraftOrder"));
        pick.setKeeper(resultSet.getBoolean("Keeper"));
        status.getPicks().add(pick);
      }
    } finally {
      close(resultSet);
    }
  }

  public GraphsData getGraphsData(int team) {
    // TODO(kcp)
    GraphsData graphsData = beanFactory.createGraphsData().as();
    Map<PlayerColumn, Float> myValues = Maps.newHashMap();
    graphsData.setMyValues(myValues);
    Map<PlayerColumn, Float> avgValues = Maps.newHashMap();
    graphsData.setAvgValues(avgValues);

    // HR, RBI, OBP, SLG, RHR, SBCS, INN, K, ERA, WHIP, WL, S
    // TODO(kcp)
    myValues.put(HR, 180f);
    myValues.put(RBI, 720f);
    myValues.put(OBP, 0.3f);
    myValues.put(SLG, 0.4f);
    myValues.put(RHR, 560f);
    myValues.put(SBCS, 180f);
    myValues.put(INN, 1400f);
    myValues.put(K, 1400f);
    myValues.put(ERA, 3.5f);
    myValues.put(WHIP, 1.2f);
    myValues.put(WL, 75f);
    myValues.put(S, 60f);
    avgValues.put(HR, 140f);
    avgValues.put(RBI, 790f);
    avgValues.put(OBP, 0.32f);
    avgValues.put(SLG, 0.39f);
    avgValues.put(RHR, 570f);
    avgValues.put(SBCS, 100f);
    avgValues.put(INN, 1450f);
    avgValues.put(K, 1200f);
    avgValues.put(ERA, 3.9f);
    avgValues.put(WHIP, 1.1f);
    avgValues.put(WL, 76f);
    avgValues.put(S, 80f);

    return graphsData;
  }

  private static List<String> splitEligibilities(String eligibility) {
    return eligibility.isEmpty()
        ? Lists.newArrayList("DH")
        : Lists.newArrayList(eligibility.split(","));
  }

  private ResultSet executeQuery(String sql) throws SQLException {
    Statement statement = db.getConnection().createStatement();
    return statement.executeQuery(sql);
  }

  private Statement executeUpdate(String sql) throws SQLException {
    Statement statement = db.getConnection().createStatement();
    statement.executeUpdate(sql);
    return statement;
  }

  private static void close(ResultSet resultSet) throws SQLException {
    if (resultSet == null) {
      return;
    }
    Statement statement = resultSet.getStatement();
    Connection connection = statement.getConnection();
    resultSet.close();
    statement.close();
    connection.close();
  }

  private void close(Statement statement) throws SQLException {
    Connection connection = statement.getConnection();
    statement.close();
    connection.close();
  }
}