package com.mayhew3.drafttower.client.players.unclaimed;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.mayhew3.drafttower.client.players.PositionFilter;
import com.mayhew3.drafttower.shared.*;

import java.util.*;
import java.util.Map.Entry;

/**
 * Encapsulates the list of players for a given data set; handles sorting, filtering, etc.
 */
public class PlayerList {
  @VisibleForTesting final Map<SortSpec, List<Player>> playersBySortCol = new HashMap<>();
  private final Set<Long> pickedPlayers = new HashSet<>();

  public PlayerList(List<Player> players,
      PlayerColumn defaultSortCol,
      boolean defaultSortAscending) {
    playersBySortCol.put(new SortSpec(defaultSortCol, defaultSortAscending), players);
  }

  public Iterable<Player> getPlayers(TableSpec tableSpec,
      int rowStart, int rowCount,
      final PositionFilter positionFilter,
      final EnumSet<Position> excludedPositions,
      final boolean hideInjuries,
      final String nameFilter) {
    SortSpec sortSpec = new SortSpec(tableSpec.getSortCol(), tableSpec.isAscending());
    if (!playersBySortCol.containsKey(sortSpec)) {
      List<Player> players = playersBySortCol.values().iterator().next();
      Comparator<Player> comparator = sortSpec.getColumn() == PlayerColumn.WIZARD
          ? positionFilter.getWizardComparator(sortSpec.isAscending())
          : sortSpec.getColumn().getComparator(sortSpec.isAscending());
      playersBySortCol.put(sortSpec,
          Ordering.from(comparator).sortedCopy(players));
    }
    return Iterables.limit(Iterables.skip(Iterables.filter(
        playersBySortCol.get(sortSpec),
        new Predicate<Player>() {
          @Override
          public boolean apply(Player player) {
            return (nameFilter == null
                || PlayerColumn.NAME.get(player).toLowerCase()
                    .contains(nameFilter.toLowerCase()))
                && (!hideInjuries || player.getInjury() == null)
                && positionFilter.apply(player, excludedPositions)
                && !pickedPlayers.contains(player.getPlayerId());
          }
        }), rowStart), rowCount);
  }

  public int getTotalPlayers() {
    return playersBySortCol.values().iterator().next().size() - pickedPlayers.size();
  }

  public void ensurePlayersRemoved(List<DraftPick> picks) {
    pickedPlayers.clear();
    for (DraftPick pick : picks) {
      pickedPlayers.add(pick.getPlayerId());
    }
  }

  public void updatePlayerRank(long playerId, int prevRank, int newRank) {
    List<Player> players = playersBySortCol.values().iterator().next();
    int lesserRank = prevRank + 1;
    int greaterRank = newRank;
    if (prevRank > newRank) {
      lesserRank = newRank;
      greaterRank = prevRank - 1;
    }
    // Update all players.
    for (Player player : players) {
      if (player.getPlayerId() == playerId) {
        player.setMyRank(Integer.toString(newRank));
      } else {
        int rank = Integer.parseInt(player.getMyRank());
        if (rank >= lesserRank && rank <= greaterRank) {
          if (prevRank > newRank) {
            player.setMyRank(Integer.toString(rank + 1));
          } else {
            player.setMyRank(Integer.toString(rank - 1));
          }
        }
      }
    }
    // Clear any cached sort orders sorted by rank.
    Set<SortSpec> keysToRemove = new HashSet<>();
    for (Entry<SortSpec, List<Player>> entry : playersBySortCol.entrySet()) {
      SortSpec sortSpec = entry.getKey();
      if (sortSpec.getColumn() == PlayerColumn.MYRANK) {
        keysToRemove.add(sortSpec);
      }
    }
    for (SortSpec sortSpec : keysToRemove) {
      playersBySortCol.remove(sortSpec);
    }
    // Ensure we don't hit the server again if the only sort order we had was by rank.
    if (playersBySortCol.isEmpty()) {
      playersBySortCol.put(new SortSpec(PlayerColumn.MYRANK, true),
          Ordering.from(PlayerColumn.MYRANK.getComparator(true)).sortedCopy(players));
    }
  }
}