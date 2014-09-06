package com.mayhew3.drafttower.client.players.unclaimed;

import com.google.common.collect.Lists;
import com.google.web.bindery.autobean.vm.AutoBeanFactorySource;
import com.mayhew3.drafttower.shared.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * Tests for {@link PlayerList}.
 */
public class PlayerListTest {

  private BeanFactory beanFactory;
  private PlayerList playerList;
  private List<Player> players;

  @Before
  public void setUp() throws Exception {
    beanFactory = AutoBeanFactorySource.create(BeanFactory.class);
    players = new ArrayList<>();
    TestPlayerGenerator playerGenerator = new TestPlayerGenerator(beanFactory);
    for (int i = 0; i < 20; i++) {
      players.add(playerGenerator.generatePlayer(i,
          i < 10 ? Position.C : Position.P,
          i));
    }
    playerList = new PlayerList(players, PlayerColumn.MYRANK, EnumSet.allOf(Position.class), true);
  }

  @Test
  public void testGetPlayersSortedAscendingByRank() {
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.MYRANK, true),
        0, 20, EnumSet.allOf(Position.class), false, null).iterator();
    Assert.assertEquals(players.get(0), result.next());
    Assert.assertEquals(players.get(1), result.next());
    Assert.assertEquals(players.get(2), result.next());
    Assert.assertEquals(players.get(3), result.next());
    Assert.assertEquals(players.get(4), result.next());
  }

  @Test
  public void testGetPlayersSortedDescendingByRank() {
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.MYRANK, false),
        0, 20, EnumSet.allOf(Position.class), false, null).iterator();
    Assert.assertEquals(players.get(19), result.next());
    Assert.assertEquals(players.get(18), result.next());
    Assert.assertEquals(players.get(17), result.next());
    Assert.assertEquals(players.get(16), result.next());
    Assert.assertEquals(players.get(15), result.next());
  }

  @Test
  public void testGetPlayersSortedDescendingByOtherColumn() {
    players.get(3).setSBCS("50");
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.SBCS, false),
        0, 20, EnumSet.allOf(Position.class), false, null).iterator();
    Assert.assertEquals(players.get(3), result.next());
    Assert.assertEquals(players.get(9), result.next());
    Assert.assertEquals(players.get(8), result.next());
    Assert.assertEquals(players.get(7), result.next());
    Assert.assertEquals(players.get(6), result.next());
  }

  @Test
  public void testGetPlayersSortedDescendingByWizardNoPositionFilter() {
    players.get(3).setWizardC("50");
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.WIZARD, false),
        0, 20, EnumSet.allOf(Position.class), false, null).iterator();
    Assert.assertEquals(players.get(3), result.next());
    Assert.assertEquals(players.get(19), result.next());
    Assert.assertEquals(players.get(18), result.next());
    Assert.assertEquals(players.get(17), result.next());
    Assert.assertEquals(players.get(16), result.next());
  }

  @Test
  public void testGetPlayersSortedDescendingByWizardPositionFilter() {
    players.get(3).setWizardC("50");
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.WIZARD, false),
        0, 20, EnumSet.complementOf(EnumSet.of(Position.C)), false, null).iterator();
    Assert.assertEquals(players.get(19), result.next());
    Assert.assertEquals(players.get(18), result.next());
    Assert.assertEquals(players.get(17), result.next());
    Assert.assertEquals(players.get(16), result.next());
    Assert.assertEquals(players.get(15), result.next());
  }

  @Test
  public void testGetPlayersRowStartAndCount() {
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.MYRANK, true),
        7, 4, EnumSet.allOf(Position.class), false, null).iterator();
    Assert.assertEquals(players.get(7), result.next());
    Assert.assertEquals(players.get(8), result.next());
    Assert.assertEquals(players.get(9), result.next());
    Assert.assertEquals(players.get(10), result.next());
    Assert.assertFalse(result.hasNext());
  }

  @Test
  public void testGetPlayersPositionFilter() {
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.MYRANK, true),
        0, 20, EnumSet.of(Position.P), false, null).iterator();
    Assert.assertEquals(players.get(10), result.next());
    Assert.assertEquals(players.get(11), result.next());
    Assert.assertEquals(players.get(12), result.next());
    Assert.assertEquals(players.get(13), result.next());
    Assert.assertEquals(players.get(14), result.next());
  }

  @Test
  public void testGetPlayersHideInjuries() {
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.MYRANK, true),
        4, 20, EnumSet.allOf(Position.class), true, null).iterator();
    Assert.assertEquals(players.get(4), result.next());
    Assert.assertEquals(players.get(6), result.next());
    Assert.assertEquals(players.get(7), result.next());
    Assert.assertEquals(players.get(8), result.next());
    Assert.assertEquals(players.get(9), result.next());
  }

  @Test
  public void testGetPlayersNameFilter() {
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.MYRANK, true),
        0, 20, EnumSet.allOf(Position.class), false, "2").iterator();
    Assert.assertEquals(players.get(2), result.next());
    Assert.assertEquals(players.get(12), result.next());
    Assert.assertFalse(result.hasNext());
  }

  @Test
  public void testGetPlayersPickedPlayers() {
    playerList.ensurePlayersRemoved(Lists.newArrayList(
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 0, beanFactory),
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 1, beanFactory),
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 2, beanFactory),
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 5, beanFactory)));
    Iterator<Player> result = playerList.getPlayers(
        createTableSpec(PlayerColumn.MYRANK, true),
        0, 20, EnumSet.allOf(Position.class), false, null).iterator();
    Assert.assertEquals(players.get(3), result.next());
    Assert.assertEquals(players.get(4), result.next());
    Assert.assertEquals(players.get(6), result.next());
    Assert.assertEquals(players.get(7), result.next());
    Assert.assertEquals(players.get(8), result.next());
  }

  @Test
  public void testGetTotalPlayers() {
    Assert.assertEquals(20, playerList.getTotalPlayers());
    playerList.ensurePlayersRemoved(Lists.newArrayList(
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 0, beanFactory),
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 1, beanFactory),
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 2, beanFactory),
        DraftStatusTestUtil.createDraftPick(1, "", false, "C", 3, beanFactory)));
    Assert.assertEquals(16, playerList.getTotalPlayers());
  }

  @Test
  public void testUpdatePlayerRankIncrease() {
    playerList.updatePlayerRank(5, 5, 2);
    Assert.assertEquals("0", players.get(0).getMyRank());
    Assert.assertEquals("1", players.get(1).getMyRank());
    Assert.assertEquals("3", players.get(2).getMyRank());
    Assert.assertEquals("4", players.get(3).getMyRank());
    Assert.assertEquals("5", players.get(4).getMyRank());
    Assert.assertEquals("2", players.get(5).getMyRank());
    Assert.assertEquals("6", players.get(6).getMyRank());
  }

  @Test
  public void testUpdatePlayerRankDecrease() {
    playerList.updatePlayerRank(2, 2, 5);
    Assert.assertEquals("0", players.get(0).getMyRank());
    Assert.assertEquals("1", players.get(1).getMyRank());
    Assert.assertEquals("5", players.get(2).getMyRank());
    Assert.assertEquals("2", players.get(3).getMyRank());
    Assert.assertEquals("3", players.get(4).getMyRank());
    Assert.assertEquals("4", players.get(5).getMyRank());
    Assert.assertEquals("6", players.get(6).getMyRank());
  }

  @Test
  public void testUpdatePlayerRankNoOp() {
    playerList.updatePlayerRank(4, 4, 4);
    Assert.assertEquals("0", players.get(0).getMyRank());
    Assert.assertEquals("1", players.get(1).getMyRank());
    Assert.assertEquals("2", players.get(2).getMyRank());
    Assert.assertEquals("3", players.get(3).getMyRank());
    Assert.assertEquals("4", players.get(4).getMyRank());
    Assert.assertEquals("5", players.get(5).getMyRank());
    Assert.assertEquals("6", players.get(6).getMyRank());
  }

  @Test
  public void testUpdatePlayerRankClearsSortCache() {
    playerList.getPlayers(createTableSpec(PlayerColumn.AB, false),
        0, 20, EnumSet.allOf(Position.class), false, null);
    playerList.updatePlayerRank(1, 1, 3);
    Assert.assertFalse(playerList.playersBySortCol.containsKey(
        new SortSpec(PlayerColumn.MYRANK, EnumSet.allOf(Position.class), true)));
  }

  @Test
  public void testUpdatePlayerRankRestoresSortCacheWhenEmpty() {
    playerList.updatePlayerRank(1, 1, 3);
    Assert.assertTrue(playerList.playersBySortCol.containsKey(
        new SortSpec(PlayerColumn.MYRANK, EnumSet.allOf(Position.class), true)));
  }

  private TableSpec createTableSpec(PlayerColumn playerColumn, boolean ascending) {
    TableSpec tableSpec = beanFactory.createTableSpec().as();
    tableSpec.setSortCol(playerColumn);
    tableSpec.setPlayerDataSet(PlayerDataSet.AVERAGES);
    tableSpec.setAscending(ascending);
    return tableSpec;
  }
}