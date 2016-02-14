package ui.components.pickers;

import backend.resource.TurboIssue;
import backend.resource.TurboMilestone;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Pair;
import util.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MilestonePickerDialog extends Dialog<Pair<ButtonType, Integer>> {
    private static final String DIALOG_TITLE = "Select Milestone";
    private static final String OPEN_MILESTONES = "Open Milestones";
    private static final String CLOSED_MILESTONES = "Closed Milestones";
    private static final String ASSIGNED_MILESTONE = "Assigned Milestone";

    private final List<PickerMilestone> originalMilestones = new ArrayList<>();
    private List<PickerMilestone> milestonesToDisplay = new ArrayList<>();
    private VBox milestoneBox;
    FlowPane openMilestones, closedMilestones, assignedMilestone;
    private TextField inputField;

    /**xxxx
     * Constructor to create a MilestonePickerDialog
     *
     * The issue and the originalMilestones list provided should come from the same repository
     * @param stage
     * @param issue
     * @param milestones
     */
    public MilestonePickerDialog(Stage stage, TurboIssue issue, List<TurboMilestone> milestones) {
        initOwner(stage);
        setTitle(DIALOG_TITLE);
        setupButtons(getDialogPane());
        convertToPickerMilestones(issue, milestones);
        initUI();
        setupKeyEvents();
    }

    private void setupKeyEvents() {
        inputField.textProperty().addListener((observable, oldValue, newValue) -> {
            processInput(newValue);
        });
    }

    private boolean hasHighlightedMilestone(List<PickerMilestone> milestones) {
        return milestones.stream()
                .filter(milestone -> milestone.isHighlighted())
                .findAny()
                .isPresent();
    }

    private void convertToPickerMilestones(TurboIssue issue, List<TurboMilestone> milestones) {
        for (int i = 0; i < milestones.size(); i++) {
            PickerMilestone convertedMilestone = new PickerMilestone(milestones.get(i), this);
            if (isExistingMilestone(issue, convertedMilestone)) {
                convertedMilestone.setExisting(true);
            }
            this.originalMilestones.add(convertedMilestone);
        }

        Collections.sort(this.originalMilestones);
        selectAssignedMilestone(issue);
    }

    private boolean isExistingMilestone(TurboIssue issue, PickerMilestone milestone) {
        if (issue.getMilestone().isPresent()) {
            return issue.getMilestone().get() == milestone.getId();
        } else {
            return false;
        }
    }

    private boolean hasMatchingMilestone(List<PickerMilestone> milestoneList) {
        return milestoneList.stream()
                .filter(milestone -> !milestone.isFaded())
                .findAny()
                .isPresent();
    }

    private void highlightFirstMatchingMilestone() {
        if (hasMatchingMilestone(this.milestonesToDisplay)) {
            this.milestonesToDisplay.stream()
                    .filter(milestone -> !milestone.isFaded())
                    .findAny()
                    .get()
                    .setHighlighted(true);
        }
    }

    private boolean hasSelectedMilestone() {
        return this.milestonesToDisplay.stream()
                .filter(milestone -> milestone.isSelected())
                .findAny()
                .isPresent();
    }

    private PickerMilestone getSelectedMilestone() {
        return this.milestonesToDisplay.stream()
                .filter(milestone -> milestone.isSelected())
                .findAny()
                .get();
    }

    private void selectAssignedMilestone(TurboIssue issue) {
        this.originalMilestones.stream()
                .filter(milestone -> isExistingMilestone(issue, milestone))
                .forEach(milestone -> milestone.setSelected(true));
    }

    private void setupButtons(DialogPane milestonePickerDialogPane) {
        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        setConfirmResultConverter(confirmButtonType);

        milestonePickerDialogPane.getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);
    }

    private void setConfirmResultConverter(ButtonType confirmButtonType) {
        setResultConverter((dialogButton) -> {

            if (hasSelectedMilestone()) {
                return new Pair<>(dialogButton, getSelectedMilestone().getId());
            }
            return new Pair<>(dialogButton, null);

        });
    }

    private void initUI() {
        milestonesToDisplay.addAll(originalMilestones);

        milestoneBox = new VBox();
        inputField = new TextField();

        assignedMilestone = createMilestoneGroup();

        milestoneBox.getChildren().add(new Label(ASSIGNED_MILESTONE));
        milestoneBox.getChildren().add(assignedMilestone);

        openMilestones = createMilestoneGroup();

        milestoneBox.getChildren().add(new Label(OPEN_MILESTONES));
        milestoneBox.getChildren().add(openMilestones);

        closedMilestones = createMilestoneGroup();

        milestoneBox.getChildren().add(new Label(CLOSED_MILESTONES));
        milestoneBox.getChildren().add(closedMilestones);

        milestoneBox.getChildren().add(inputField);

        getDialogPane().setContent(milestoneBox);
        Platform.runLater(inputField::requestFocus);
        refreshUI();
    }

    private void refreshUI() {
        populateAssignedMilestone(milestonesToDisplay, assignedMilestone);
        populateOpenMilestones(milestonesToDisplay, openMilestones);
        populateClosedMilestones(milestonesToDisplay, closedMilestones);
    }

    private void processInput(String userInput) {
        resetDisplayedMilestones();
        if (userInput.isEmpty()) {
            refreshUI();
            return;
        }

        String[] userInputWords = userInput.split(" ");
        for (int i = 0; i < userInputWords.length; i++) {
            String currentWord = userInputWords[i];
            if (i < userInputWords.length - 1 || userInput.endsWith(" ")) {
                toggleMilestone(currentWord);
            } else {
                filterMilestone(currentWord);
            }
        }

        refreshUI();
    }

    private void resetDisplayedMilestones() {
        milestonesToDisplay.clear();
        originalMilestones.stream()
                .forEach(milestone -> {
                    milestonesToDisplay.add(new PickerMilestone(milestone, this));
                });
    }

    private void filterMilestone(String query) {
        milestonesToDisplay.stream()
                .forEach(milestone -> {
                    boolean matchQuery = Utility.containsIgnoreCase(milestone.getTitle(), query);
                    milestone.setFaded(!matchQuery);
                });

        highlightFirstMatchingMilestone();
    }

    private void populateAssignedMilestone(List<PickerMilestone> pickerMilestoneList, FlowPane assignedMilestoneStatus) {
        assignedMilestoneStatus.getChildren().clear();
        boolean hasSuggestion = hasHighlightedMilestone(pickerMilestoneList);

        updateExistingMilestones(pickerMilestoneList, assignedMilestoneStatus, hasSuggestion);
        addSeparator(assignedMilestoneStatus);
        updateNewlyAddedMilestone(pickerMilestoneList, assignedMilestoneStatus, hasSuggestion);
        updateSuggestedMilestone(pickerMilestoneList, assignedMilestoneStatus, hasSuggestion);
    }

    private void addSeparator(FlowPane assignedMilestoneStatus) {
        assignedMilestoneStatus.getChildren().add(new Label("|"));
    }

    private void updateSuggestedMilestone(List<PickerMilestone> pickerMilestoneList, FlowPane assignedMilestoneStatus, boolean hasSuggestion) {
        pickerMilestoneList.stream()
                .filter(milestone -> !milestone.isExisting() && milestone.isHighlighted() && !milestone.isSelected())
                .forEach(milestone -> assignedMilestoneStatus.getChildren().add(milestone.getNewlyAssignedMilestoneNode(hasSuggestion)));
    }

    private void updateNewlyAddedMilestone(List<PickerMilestone> pickerMilestoneList, FlowPane assignedMilestoneStatus, boolean hasSuggestion) {
        pickerMilestoneList.stream()
                .filter(milestone -> milestone.isSelected() && !milestone.isExisting())
                .forEach(milestone -> assignedMilestoneStatus.getChildren().add(milestone.getNewlyAssignedMilestoneNode(hasSuggestion)));
    }

    private void updateExistingMilestones(List<PickerMilestone> pickerMilestoneList, FlowPane assignedMilestoneStatus, boolean hasSuggestion) {
        if (hasExistingMilestone(pickerMilestoneList)) {
            PickerMilestone existingMilestone = getExistingMilestone(pickerMilestoneList);
            assignedMilestoneStatus.getChildren().add(existingMilestone.getExistingMilestoneNode(hasSuggestion));
        }
    }

    private boolean hasExistingMilestone(List<PickerMilestone> milestoneList) {
        return milestoneList.stream()
                .filter(milestone -> milestone.isExisting())
                .findAny()
                .isPresent();
    }

    private PickerMilestone getExistingMilestone(List<PickerMilestone> milestoneList) {
        return milestoneList.stream()
                .filter(milestone -> milestone.isExisting())
                .findAny()
                .get();
    }

    private void populateClosedMilestones(List<PickerMilestone> pickerMilestoneList, FlowPane closedMilestones) {
        closedMilestones.getChildren().clear();
        pickerMilestoneList.stream()
                .filter(milestone -> !milestone.isOpen())
                .forEach(milestone -> closedMilestones.getChildren().add(milestone.getNode()));
    }

    private void populateOpenMilestones(List<PickerMilestone> pickerMilestoneList, FlowPane openMilestones) {
        openMilestones.getChildren().clear();
        pickerMilestoneList.stream()
                .filter(milestone -> milestone.isOpen())
                .forEach(milestone -> openMilestones.getChildren().add(milestone.getNode()));
    }

    private FlowPane createMilestoneGroup() {
        FlowPane milestoneGroup = new FlowPane();
        milestoneGroup.setPadding(new Insets(3));
        milestoneGroup.setHgap(3);
        milestoneGroup.setVgap(3);
        milestoneGroup.setStyle("-fx-border-radius: 3;-fx-background-color: white;-fx-border-color: black;");
        return milestoneGroup;
    }

    private String getMilestoneName(String query) {
        if (hasExactlyOneMatchingMilestone(milestonesToDisplay, query)) {
            return getMatchingMilestoneName(milestonesToDisplay, query);
        }
        return null;
    }

    private boolean hasExactlyOneMatchingMilestone(List<PickerMilestone> milestoneList, String query) {
        return milestoneList.stream()
                .filter(milestone -> Utility.containsIgnoreCase(milestone.getTitle(), query))
                .count() == 1;
    }

    private String getMatchingMilestoneName(List<PickerMilestone> milestoneList, String query) {
        return milestoneList.stream()
                .filter(milestone -> Utility.containsIgnoreCase(milestone.getTitle(), query))
                .findFirst()
                .get()
                .getTitle();
    }

    /**
     * Finds the PickerMilestone in the milestoneToDisplay list which has milestoneQuery in title,
     * then toggles the selection status
     * @param milestoneQuery
     */
    public void toggleMilestone(String milestoneQuery) {
        String milestoneName = getMilestoneName(milestoneQuery);
        if (milestoneName == null) return;
        this.milestonesToDisplay.stream()
                .forEach(milestone -> {
                    milestone.setSelected(milestone.getTitle().equals(milestoneName)
                            && !milestone.isSelected());
                });
    }
}
