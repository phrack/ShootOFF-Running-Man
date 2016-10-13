package com.shootoff.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import com.shootoff.camera.Shot;
import com.shootoff.targets.Hit;
import com.shootoff.targets.Target;
import com.shootoff.targets.TargetRegion;
import com.shootoff.util.TimerPool;

import javafx.application.Platform;
import javafx.geometry.Dimension2D;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

public class RunningMan extends ProjectorTrainingExerciseBase implements TrainingExercise {
	private final ChoiceBox<Integer> obstacleTargetsChoiceBox = new ChoiceBox<>();
	private final ChoiceBox<Speed> speedChoiceBox = new ChoiceBox<>();
	private final ChoiceBox<StartingSide> startingSideChoiceBox = new ChoiceBox<>();
	private final ChoiceBox<ObstacleMode> obstacleModeChoiceBox = new ChoiceBox<>();

	private final List<Target> targets = new ArrayList<Target>();
	private final AtomicBoolean resetting = new AtomicBoolean(false);
	private final AtomicBoolean refreshingTargets = new AtomicBoolean(false);
	private final AtomicBoolean destroying = new AtomicBoolean(false);
	
	private int targetCount;
	private int currentTargetIndex;
	private StartingSide runningDirection;
	private int score = 0;

	public RunningMan() {
	}

	public RunningMan(List<Target> targets) {
		super(targets);
	}

	@Override
	public void init() {
		initTargets();
		initGui();
		doRun();
	}

	private void initTargets() {
		refreshingTargets.set(true);
		
		for (Target t : targets) removeTarget(t);
		targets.clear();

		// We intentionally do not use this target for anything but for size
		// measurements, otherwise it's a pain to ensure the first target
		// can be an obstacle
		final File runningManFile = new File("@target/running_man.target");
		final Optional<Target> runningManTarget = addTarget(runningManFile, 10, 10);
		if (!runningManTarget.isPresent()) return;
		removeTarget(runningManTarget.get());

		// Determine how many targets we can add if spaced equally apart on the
		// arena
		final int targetMargin = 30;
		final int targetGap = 10;

		final Dimension2D targetSize = runningManTarget.get().getDimension();

		targetCount = (int) ((getArenaWidth() - (targetMargin * 2)) / (targetSize.getWidth() + targetGap));

		if (targetCount < 1) return;
		
		// Allow two less than the number of possible targets as obstacles
		final int savedObstacleCount;
		if (obstacleTargetsChoiceBox.getSelectionModel().isEmpty()) {
			savedObstacleCount = 1;
		} else {
			savedObstacleCount = obstacleTargetsChoiceBox.getSelectionModel().getSelectedItem();
		}

		obstacleTargetsChoiceBox.getItems().clear();

		for (int i = 0; i < targetCount - 1; i++) {
			obstacleTargetsChoiceBox.getItems().add(i);
		}

		obstacleTargetsChoiceBox.getSelectionModel().select(savedObstacleCount);

		// Calculate the y-dimension to vertical center the targets
		final double targetY = (getArenaHeight() / 2) - (targetSize.getHeight() / 2);

		// Place targets
		final File obstacleFile = new File("@target/obstacle.target");
		final Random rand = new Random();
		double targetX = targetMargin;
		
		final List<Target> obstacleTargets = new ArrayList<>();

		for (int i = 0; i < obstacleTargetsChoiceBox.getSelectionModel().getSelectedItem(); i++) {
			final Optional<Target> t = addTarget(obstacleFile, targetX, targetY);
			if (t.isPresent()) {
				obstacleTargets.add(t.get());
			}
		}
		
		final List<Target> runningManTargets = new ArrayList<>();
		
		for (int i = 0; i < targetCount - obstacleTargets.size(); i++) {
			final Optional<Target> t = addTarget(runningManFile, targetX, targetY);
			if (t.isPresent()) {
				runningManTargets.add(t.get());
			}
		}
		
		for (int i = 0; i < targetCount; i++) {
			final Target target;

			if (runningManTargets.isEmpty() || 
					(!obstacleTargets.isEmpty() && rand.nextBoolean())) {
				target = obstacleTargets.get(0);
				obstacleTargets.remove(0);
				
				if (ObstacleMode.SURPRISE.equals(obstacleModeChoiceBox.getSelectionModel().getSelectedItem())) {
					target.setVisible(false);
				}
				
			} else {
				target = runningManTargets.get(0);
				runningManTargets.remove(0);
				target.setVisible(false);
			}

			target.setPosition(targetX, targetY);
			targetX += targetSize.getWidth() + targetGap;
			targets.add(target);
		}

		refreshingTargets.set(false);
	}

	private void initGui() {
		showTextOnFeed("Score: 0");
		
		final GridPane exercisePane = new GridPane();

		exercisePane.add(new Label("Obstacle Targets"), 0, 0);
		exercisePane.add(obstacleTargetsChoiceBox, 1, 0);

		obstacleTargetsChoiceBox.getSelectionModel().selectedItemProperty().addListener((Observable, oldValue, newValue) -> {
			if (resetting.get() || refreshingTargets.get()) return;
			
			initTargets();
		});

		exercisePane.add(new Label("Speed"), 0, 1);
		exercisePane.add(speedChoiceBox, 1, 1);
		speedChoiceBox.getItems().setAll(Speed.values());
		speedChoiceBox.getSelectionModel().select(Speed.SLOW);

		exercisePane.add(new Label("Starting Side"), 0, 2);
		exercisePane.add(startingSideChoiceBox, 1, 2);
		startingSideChoiceBox.getItems().setAll(StartingSide.values());
		startingSideChoiceBox.getSelectionModel().select(StartingSide.LEFT);

		exercisePane.add(new Label("Obstacle Mode"), 0, 3);
		exercisePane.add(obstacleModeChoiceBox, 1, 3);
		obstacleModeChoiceBox.getItems().setAll(ObstacleMode.values());
		obstacleModeChoiceBox.getSelectionModel().select(ObstacleMode.KNOWN);
		
		obstacleModeChoiceBox.getSelectionModel().selectedItemProperty().addListener((Observable, oldValue, newValue) -> {
			if (resetting.get() || refreshingTargets.get()) return;
			
			initTargets();
		});

		final int prefWidth = 200;
		obstacleTargetsChoiceBox.setPrefWidth(prefWidth);
		speedChoiceBox.setPrefWidth(prefWidth);
		startingSideChoiceBox.setPrefWidth(prefWidth);
		obstacleModeChoiceBox.setPrefWidth(prefWidth);

		addExercisePane(exercisePane);
	}

	private enum Speed {
		VERY_SLOW("Very Slow"), SLOW("Slow"), MEDIUM("Medium"), FAST("Fast"), VERY_FAST("Very Fast");

		private String label;

		Speed(String label) {
			this.label = label;
		}

		public String toString() {
			return label;
		}
	}

	private enum StartingSide {
		LEFT("Left"), RIGHT("Right"), RANDOM("Random");

		private String label;

		StartingSide(String label) {
			this.label = label;
		}

		public String toString() {
			return label;
		}
	}

	private enum ObstacleMode {
		KNOWN("Known Obstructions"), SURPRISE("Surprise Obstructions");

		private String label;

		ObstacleMode(String label) {
			this.label = label;
		}

		public String toString() {
			return label;
		}
	}
	
	private void doRun() {
		runningDirection = StartingSide.LEFT;
		currentTargetIndex = getStartIndex();
		if (currentTargetIndex > 0) runningDirection = StartingSide.RIGHT;

		targets.get(currentTargetIndex).setVisible(true);

		final Runnable toggleTarget = new Runnable() {
			@Override
			public void run() {
				hideTarget(targets.get(currentTargetIndex));
				if (StartingSide.LEFT.equals(runningDirection)) {
					currentTargetIndex++;
				} else {
					currentTargetIndex--;
				}
				
				if (currentTargetIndex >= targetCount || currentTargetIndex < 0) {
					Platform.runLater(() -> {
						initTargets();
						currentTargetIndex = getStartIndex();
						targets.get(currentTargetIndex).setVisible(true);
						// Note that direction changes only take effect after
						// the current run is complete
						runningDirection = currentTargetIndex == 0 ? StartingSide.LEFT : StartingSide.RIGHT;

						if (!resetting.get() && !destroying.get()) {
							TimerPool.schedule(this, getRunningDelay());
						}
					});
				} else {
					targets.get(currentTargetIndex).setVisible(true);
					
					if (!resetting.get() && !destroying.get()) {
						TimerPool.schedule(this, getRunningDelay());
					}
				}
			}
		};

		TimerPool.schedule(toggleTarget, getRunningDelay());
	}
	
	private int getStartIndex() {
		switch (startingSideChoiceBox.getSelectionModel().getSelectedItem()) {
		case LEFT:
			return 0;
		case RIGHT:
			return targetCount - 1;
		case RANDOM:
			if (new Random().nextBoolean()) {
				return 0;
			} else {
				return targetCount - 1;
			}
		}
		
		return 0;
	}
	
	private int getRunningDelay() {
		switch (speedChoiceBox.getSelectionModel().getSelectedItem()) {
		case VERY_SLOW:
			return 3000;
		case SLOW:
			return 2000;
		case MEDIUM:
			return 1000;
		case FAST:
			return 500;
		case VERY_FAST:
			return 200;
		}
		
		return 5000;
	}
	
	private void hideTarget(Target t) {
		// Don't hide obstacles unless we are in surprised mode
		if (isObstacle(t)) {
			if (ObstacleMode.SURPRISE.equals(obstacleModeChoiceBox.getSelectionModel().getSelectedItem())) {
				t.setVisible(false);
			}
		} else {
			t.setVisible(false);
		}
	}
	
	private boolean isObstacle(Target t) {
		for (TargetRegion r : t.getRegions()) {
			if (r.tagExists("subtarget") && 
					"obstacle".equals(r.getTag("subtarget"))) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public void reset(List<Target> targets) {
		resetting.set(true);
		initTargets();
		currentTargetIndex = getStartIndex();
		score = 0;
		showTextOnFeed("Score: 0");
		resetting.set(false);
	}

	@Override
	public void shotListener(Shot shot, Optional<Hit> hit) {
		if (hit.isPresent() && hit.get().getTarget().isVisible()) {
			final TargetRegion hitRegion = hit.get().getHitRegion();
			if (hitRegion.tagExists("subtarget")) {
				if ("running_man".equals(hitRegion.getTag("subtarget"))) {
					score++;
				} else if ("obstacle".equals(hitRegion.getTag("subtarget"))) {
					TextToSpeech.say(String.format("Your score was %d", score));
					score = 0;
					
					if (Platform.isFxApplicationThread()) {
						initTargets();
					} else {
						Platform.runLater(() -> initTargets());
					}
				}
				
				showTextOnFeed(String.format("Score: %d", score));
			}
		}
	}

	@Override
	public void targetUpdate(Target target, TargetChange change) {
		if (resetting.get() || refreshingTargets.get() || destroying.get()) return;
		
		if (TargetChange.REMOVED.equals(change) && 
				targets.contains(target)) {
			targets.remove(target);
			
			if (targets.isEmpty()) {
				initTargets();
			}
		}
	}

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("Running Man", "1.0", "phrack",
				"Shoot gray targets as they pop up while avoiding red targets. "
						+ "You get a point for each hit on a gray target, but your score "
						+ "resets if you hit a red target. Every time the man runs off the "
						+ "screen the obstacles are re-arranged.");
	}
	
	@Override
	public void destroy() {
		destroying.set(true);
		super.destroy();
	}
}
