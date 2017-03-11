package fr.xephi.authme.data.limbo;

import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.SpawnLoader;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Test for {@link LimboService}.
 */
@RunWith(MockitoJUnitRunner.class)
public class LimboServiceTest {

    @InjectMocks
    private LimboService limboService;

    @Mock
    private SpawnLoader spawnLoader;

    @Mock
    private PermissionsManager permissionsManager;

    @Mock
    private Settings settings;

    @Mock
    private LimboPlayerTaskManager taskManager;

    @BeforeClass
    public static void initLogger() {
        TestHelper.setupLogger();
    }

    @Before
    public void mockSettings() {
        given(settings.getProperty(RestrictionSettings.ALLOW_UNAUTHED_MOVEMENT)).willReturn(false);
        given(settings.getProperty(RestrictionSettings.REMOVE_SPEED)).willReturn(true);
    }

    @Test
    public void shouldCreateLimboPlayer() {
        // given
        Player player = newPlayer("Bobby", true, 0.3f, false, 0.2f);
        Location playerLoc = mock(Location.class);
        given(spawnLoader.getPlayerLocationOrSpawn(player)).willReturn(playerLoc);
        given(permissionsManager.hasGroupSupport()).willReturn(true);
        given(permissionsManager.getPrimaryGroup(player)).willReturn("permgrwp");

        // when
        limboService.createLimboPlayer(player, true);

        // then
        verify(taskManager).registerMessageTask(eq(player), any(LimboPlayer.class), eq(true));
        verify(taskManager).registerTimeoutTask(eq(player), any(LimboPlayer.class));
        verify(player).setAllowFlight(false);
        verify(player).setFlySpeed(0.0f);
        verify(player).setWalkSpeed(0.0f);

        LimboPlayer limbo = getLimboMap().get("bobby");
        assertThat(limbo, not(nullValue()));
        assertThat(limbo.isOperator(), equalTo(true));
        assertThat(limbo.getWalkSpeed(), equalTo(0.3f));
        assertThat(limbo.isCanFly(), equalTo(false));
        assertThat(limbo.getFlySpeed(), equalTo(0.2f));
        assertThat(limbo.getLocation(), equalTo(playerLoc));
        assertThat(limbo.getGroup(), equalTo("permgrwp"));
    }

    @Test
    public void shouldNotKeepOpStatusForUnregisteredPlayer() {
        // given
        Player player = newPlayer("CharleS", true, 0.1f, true, 0.4f);
        Location playerLoc = mock(Location.class);
        given(spawnLoader.getPlayerLocationOrSpawn(player)).willReturn(playerLoc);
        given(permissionsManager.hasGroupSupport()).willReturn(false);

        // when
        limboService.createLimboPlayer(player, false);

        // then
        verify(taskManager).registerMessageTask(eq(player), any(LimboPlayer.class), eq(false));
        verify(taskManager).registerTimeoutTask(eq(player), any(LimboPlayer.class));
        verify(permissionsManager, only()).hasGroupSupport();
        verify(player).setAllowFlight(false);
        verify(player).setFlySpeed(0.0f);
        verify(player).setWalkSpeed(0.0f);

        LimboPlayer limbo = getLimboMap().get("charles");
        assertThat(limbo, not(nullValue()));
        assertThat(limbo.isOperator(), equalTo(false));
        assertThat(limbo.getWalkSpeed(), equalTo(0.1f));
        assertThat(limbo.isCanFly(), equalTo(true));
        assertThat(limbo.getFlySpeed(), equalTo(0.4f));
        assertThat(limbo.getLocation(), equalTo(playerLoc));
        assertThat(limbo.getGroup(), equalTo(""));
    }

    @Test
    public void shouldClearTasksOnAlreadyExistingLimbo() {
        // given
        LimboPlayer limbo = mock(LimboPlayer.class);
        getLimboMap().put("carlos", limbo);
        Player player = newPlayer("Carlos");

        // when
        limboService.createLimboPlayer(player, false);

        // then
        verify(limbo).clearTasks();
        assertThat(getLimboMap().get("carlos"), both(not(sameInstance(limbo))).and(not(nullValue())));
    }

    @Test
    public void shouldRestoreData() {
        // given
        Player player = newPlayer("John", true, 0.4f, false, 0.2f);
        LimboPlayer limbo = Mockito.spy(convertToLimboPlayer(player, null, ""));
        getLimboMap().put("john", limbo);

        // when
        limboService.restoreData(player);

        // then
        verify(player).setOp(true);
        verify(player).setWalkSpeed(0.4f);
        verify(player).setAllowFlight(false);
        verify(player).setFlySpeed(0.2f);
        verify(limbo).clearTasks();
        assertThat(getLimboMap(), anEmptyMap());
    }

    @Test
    public void shouldHandleMissingLimboPlayerWhileRestoring() {
        // given
        Player player = newPlayer("Test");

        // when
        limboService.restoreData(player);

        // then
        verify(player, only()).getName();
    }

    @Test
    public void shouldReplaceTasks() {
        // given
        LimboPlayer limbo = mock(LimboPlayer.class);
        getLimboMap().put("jeff", limbo);
        Player player = newPlayer("JEFF");


        // when
        limboService.replaceTasksAfterRegistration(player);

        // then
        verify(taskManager).registerTimeoutTask(player, limbo);
        verify(taskManager).registerMessageTask(player, limbo, true);
    }

    @Test
    public void shouldHandleMissingLimboForReplaceTasks() {
        // given
        Player player = newPlayer("ghost");

        // when
        limboService.replaceTasksAfterRegistration(player);

        // then
        verifyZeroInteractions(taskManager);
    }

    private static Player newPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }

    private static Player newPlayer(String name, boolean isOp, float walkSpeed, boolean canFly, float flySpeed) {
        Player player = newPlayer(name);
        given(player.isOp()).willReturn(isOp);
        given(player.getWalkSpeed()).willReturn(walkSpeed);
        given(player.getAllowFlight()).willReturn(canFly);
        given(player.getFlySpeed()).willReturn(flySpeed);
        return player;
    }

    private static LimboPlayer convertToLimboPlayer(Player player, Location location, String group) {
        return new LimboPlayer(location, player.isOp(), group, player.getAllowFlight(),
            player.getWalkSpeed(), player.getFlySpeed());
    }

    private Map<String, LimboPlayer> getLimboMap() {
        return ReflectionTestUtils.getFieldValue(LimboService.class, limboService, "entries");
    }
}
