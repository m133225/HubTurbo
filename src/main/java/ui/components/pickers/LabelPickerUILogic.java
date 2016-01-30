package ui.components.pickers;

import backend.resource.TurboIssue;
import backend.resource.TurboLabel;
import util.Utility;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LabelPickerUILogic {

    private final TurboIssue issue;
    private final LabelPickerDialog dialog;
    private List<TurboLabel> repoLabels;
    private final List<PickerLabel> modificationLabels = new ArrayList<>();
    private List<PickerLabel> matchingLabels;
    private final Map<String, Boolean> labelGroups = new HashMap<>();
    private final Map<String, Boolean> activeLabels = new HashMap<>();
    private Optional<String> possibleLabel = Optional.empty();

    private String previousQuery = "";
    private int previousNumberOfTextElements = 0;

    LabelPickerUILogic(TurboIssue issue, List<TurboLabel> repoLabels, LabelPickerDialog dialog) {
        this.issue = issue;
        this.dialog = dialog;
        populateAllLabels(repoLabels);
        addExistingLabels();
        restoreMatchingLabels();
        populatePanes();
    }

    public LabelPickerUILogic(TurboIssue issue, List<TurboLabel> repoLabels) {
        this.issue = issue;
        this.dialog = null;
        populateAllLabels(repoLabels);
        addExistingLabels();
        restoreMatchingLabels();
        populatePanes();
    }

    private void populateAllLabels(List<TurboLabel> repoLabels) {
        this.repoLabels = new ArrayList<>(repoLabels);
        Collections.sort(this.repoLabels);
        // populate activeLabels by going through repoLabels and seeing which ones currently exist
        // in issue.getLabels()
        repoLabels.forEach(label -> {
            // matching with exact labels so no need to worry about capitalisation
            activeLabels.put(label.getActualName(), isAnExistingLabel(label.getActualName()));
            if (label.getGroup().isPresent() && !labelGroups.containsKey(label.getGroup().get())) {
                labelGroups.put(label.getGroup().get(), label.isExclusive());
            }
        });
    }

    private void populatePanes() {
        if (dialog != null) dialog.populatePanes(getExistingLabels(), getAddedLabels(), matchingLabels, labelGroups);
    }

    /**
     * This method is called when the user manually clicks on the label
     * @param name
     */
    public void toggleLabel(String name) {
        removePossibleLabel();
        updateLabel(name);
        restoreMatchingLabels();
        populatePanes();
    }

    /**
     * This method should be called internally when the user presses space in the text field
     */
    public void toggleHighlightedLabel() {
        if (!matchingLabels.isEmpty() && hasHighlightedLabel()) {
            toggleLabel(
                    matchingLabels.stream().filter(PickerLabel::isHighlighted).findFirst().get().getActualName());
        }
    }

    /**
     * This method should be called by the UI's text field upon any change
     *
     * @param text
     */
    public void processTextFieldChange(String text) {
        if (hasTextElement(text) && hasNewQuery(text)) {
            registerQuery(getQuery(text));
            updateTextProperties(text);

            populatePanes();
        }
    }

    private boolean hasNewQuery(String text) {
        String query = getQuery(text);
        int noOfTextElements = getNumberOfTextElements(text);
        return previousNumberOfTextElements != noOfTextElements || !query.equals(previousQuery);
    }

    private void updateTextProperties(String text) {
        previousNumberOfTextElements = getNumberOfTextElements(text);
        previousQuery = getQuery(text);
    }

    private void registerQuery(String query) {
        // group check
        if (isGroupQuery(query)) {
            String groupQuery = getGroupQuery(query);
            String nameQuery = getNameQuery(query);
            updateMatchingLabels(groupQuery, nameQuery);
        } else {
            updateMatchingLabels(query);
        }


        if (hasHighlightedLabel()) {
            updatePossibleLabel(getHighlightedLabelName());
        } else {
            removePossibleLabel();
        }
    }

    private boolean isGroupQuery(String query) {
        return TurboLabel.getDelimiter(query).isPresent();
    }

    private int getNumberOfTextElements(String text) {
        return text.split(" ").length;
    }

    private String getGroupQuery(String query) {
        String delimiter = TurboLabel.getDelimiter(query).get();
        String[] queryArray = query.split(Pattern.quote(delimiter));
        return queryArray[0];
    }

    private String getNameQuery(String query) {
        String delimiter = TurboLabel.getDelimiter(query).get();
        String[] queryArray = query.split(Pattern.quote(delimiter));
        return (queryArray.length > 1) ? queryArray[1] : "";
    }

    private String getQuery(String text) {
        String[] textArray = text.split(" ");
        return textArray[textArray.length - 1];
    }

    private boolean hasTextElement(String text) {
        return text.split(" ").length > 0;
    }

    /*
    * Top pane methods do not need to worry about capitalisation because they
    * all deal with actual labels.
    */
    @SuppressWarnings("unused")
    private void ______TOP_PANE______() {}

    private void addExistingLabels() {
        // used once to populate modificationLabels at the start
        repoLabels.stream()
                .filter(label -> issue.getLabels().contains(label.getActualName()))
                .forEach(label -> modificationLabels.add(new PickerLabel(label, this, true)));
    }

    private void updateLabel(String name) {
        Optional<TurboLabel> turboLabel =
                repoLabels.stream().filter(label -> label.getActualName().equals(name)).findFirst();
        if (turboLabel.isPresent()) {
            if (turboLabel.get().isExclusive() && !isAnActiveLabel(name)) {
                removeGroupLabels(turboLabel);
                setLabel(name, true);
            } else {
                setLabel(name, !isAnActiveLabel(name));
            }
        }
    }

    private void removeGroupLabels(Optional<TurboLabel> turboLabel) {
        String group = turboLabel.get().getGroup().get();
        repoLabels
                .stream()
                .filter(TurboLabel::isExclusive)
                .filter(label -> label.getGroup().get().equals(group))
                .forEach(label -> setLabel(label.getActualName(), false));
    }

    private void setLabel(String name, boolean isAdd) {
        // adds new labels to the end of the list
        activeLabels.put(name, isAdd); // update activeLabels first
        if (isAnExistingLabel(name)) {
            if (isAdd) {
                restoreLabel(name);
            } else {
                cancelLabel(name);
            }
        } else {
            if (isAdd) {
                addLabel(name);
            } else {
                removeLabel(name);
            }
        }
    }

    private void cancelLabel(String name) {
        modificationLabels.stream()
                .filter(label -> label.getActualName().equals(name))
                .findFirst()
                .ifPresent(label -> label.setIsRemoved(true));
    }

    private void removeLabel(String name) {
        modificationLabels.stream()
                .filter(label -> label.getActualName().equals(name))
                .findFirst()
                .ifPresent(label -> modificationLabels.remove(label));
    }

    private void addLabel(String name) {
        repoLabels.stream()
                .filter(label -> label.getActualName().equals(name))
                .findFirst()
                .ifPresent(label -> modificationLabels.add(new PickerLabel(label, this, true)));
    }

    private void restoreLabel(String name) {
        modificationLabels.stream()
                .filter(label -> label.getActualName().equals(name))
                .forEach(label -> {
                    label.setIsRemoved(false);
                    label.setIsFaded(false);
                });
    }

    private boolean isAModificationLabel(String name) {
        // used to prevent duplicates in modificationLabels
        return modificationLabels.stream()
                .filter(label -> label.getActualName().equals(name))
                .findAny()
                .isPresent();
    }

    private void updatePossibleLabel(String name) {
        removePossibleLabel();
        addPossibleLabel(name);
    }

    private void addPossibleLabel(String name) {
        if (!name.isEmpty()) {
            // Try to add current selection
            if (isAModificationLabel(name)) {
                removeModificationLabel(name);
            } else {
                addModificationLabel(name);
            }
            possibleLabel = Optional.of(name);
        }
    }

    private void removeModificationLabel(String name) {
        modificationLabels.stream()
                .filter(label -> label.getActualName().equals(name))
                .findFirst()
                .ifPresent(label -> {
                    label.setIsHighlighted(true);
                    if (isAnExistingLabel(name)) {
                        // if it is an existing label toggle fade and strike through
                        label.setIsFaded(isAnActiveLabel(name));
                        label.setIsRemoved(isAnActiveLabel(name));
                    } else {
                        // else set fade and strike through
                        // if space is pressed afterwards, label is removed from modificationLabels altogether
                        label.setIsFaded(true);
                        label.setIsRemoved(true);
                    }
                });
    }

    private void addModificationLabel(String name) {
        repoLabels.stream()
                .filter(label -> label.getActualName().equals(name))
                .findFirst()
                .ifPresent(label -> modificationLabels.add(
                        new PickerLabel(label, this, false, true, false, true, true)));
    }

    private void removePossibleLabel() {
        if (possibleLabel.isPresent()) {
            // restore any UI changes due to the highlighted label
            String possibleLabelName = possibleLabel.get();
            modificationLabels.stream()
                    .filter(label -> label.getActualName().equals(possibleLabelName))
                    .findFirst()
                    .ifPresent(label -> {
                        if (isAnExistingLabel(possibleLabelName)) {
                            label.setIsHighlighted(false);
                            label.setIsFaded(false);
                            label.setIsRemoved(true);
                        } else if (isAnActiveLabel(possibleLabelName)) {
                            label.setIsHighlighted(false);
                            label.setIsFaded(false);
                            label.setIsRemoved(false);
                        } else {
                            modificationLabels.remove(label);
                        }
                    });
            possibleLabel = Optional.empty();
        }
    }

    // Bottom box deals with possible matches so we usually ignore the case for these methods.
    @SuppressWarnings("unused")
    private void ______BOTTOM_BOX______() {}

    private void updateMatchingLabels(String group, String match) {
        List<String> groupNames = labelGroups.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList());

        if (isValidGroup(group, groupNames)) {
            List<String> validGroups = groupNames.stream()
                    .filter(validGroup -> Utility.startsWithIgnoreCase(validGroup, group))
                    .collect(Collectors.toList());
            // get all labels that contain search query
            // fade out labels which do not match
            matchingLabels = repoLabels
                    .stream()
                    .map(label -> new PickerLabel(label, this, false))
                    .map(label -> {
                        if (isAnActiveLabel(label.getActualName())) {
                            label.setIsSelected(true);
                        }
                        if (ifMatchesQuery(match, validGroups, label)) {
                            label.setIsFaded(true); // fade out if does not match search query
                        }
                        return label;
                    })
                    .collect(Collectors.toList());
            highlightFirstMatchingItem(match);
        } else {
            updateMatchingLabels(match);
        }
    }

    private boolean ifMatchesQuery(String match, List<String> validGroups, PickerLabel label) {
        return !label.getGroup().isPresent() ||
                !validGroups.contains(label.getGroup().get()) ||
                !Utility.containsIgnoreCase(label.getName(), match);
    }

    private boolean isValidGroup(String group, List<String> groupNames) {
        return groupNames.stream()
                .filter(validGroup -> Utility.startsWithIgnoreCase(validGroup, group))
                .findAny()
                .isPresent();
    }

    private void restoreMatchingLabels() {
        updateMatchingLabels("");
    }

    private void updateMatchingLabels(String match) {
        // get all labels that contain search query
        // fade out labels which do not match
        matchingLabels = repoLabels
                .stream()
                .map(label -> new PickerLabel(label, this, false))
                .map(label -> {
                    if (isAnActiveLabel(label.getActualName())) {
                        label.setIsSelected(true); // add tick if selected
                    }
                    if (!match.isEmpty() && !Utility.containsIgnoreCase(label.getActualName(), match)) {
                        label.setIsFaded(true); // fade out if does not match search query
                    }
                    return label;
                })
                .collect(Collectors.toList());

        if (!match.isEmpty()) highlightFirstMatchingItem(match);
    }

    /**
     * This method should be called by the UI when the user presses the up or down arrow
     * @param isDown
     */
    public void moveHighlightOnLabel(boolean isDown) {
        if (hasHighlightedLabel()) {
            // used to move the highlight on the bottom labels
            // find all matching labels
            List<PickerLabel> filteredLabels = this.matchingLabels.stream()
                    .filter(label -> !label.isFaded())
                    .collect(Collectors.toList());

            // move highlight around
            for (int i = 0; i < filteredLabels.size(); i++) {
                if (filteredLabels.get(i).isHighlighted()) {
                    if (isDown) {
                        highlightNextLabel(filteredLabels, i);
                    } else {
                        highlightPreviousLabel(filteredLabels, i);
                    }
                    populatePanes();
                    return;
                }
            }
        }
    }

    private void unhighlightCurrentLabel(PickerLabel label) {
        label.setIsHighlighted(false);
    }

    private void highlightNextLabel(List<PickerLabel> filteredLabels, int i) {
        if (i < filteredLabels.size() - 1) {
            unhighlightCurrentLabel(filteredLabels.get(i));
            setHighlightedLabel(filteredLabels.get(i + 1));
        }
    }

    private void highlightPreviousLabel(List<PickerLabel> filteredLabels, int i) {
        if (i > 0) {
            unhighlightCurrentLabel(filteredLabels.get(i));
            setHighlightedLabel(filteredLabels.get(i - 1));
        }
    }

    private void highlightFirstMatchingItem(String nameQuery) {
        List<PickerLabel> labelMatches = matchingLabels.stream()
                .filter(label -> !label.isFaded())
                .collect(Collectors.toList());

        // try to highlight labels that begin with match first
        labelMatches.stream()
                .filter(label -> Utility.startsWithIgnoreCase(label.getName(), nameQuery))
                .findFirst()
                .ifPresent(label -> label.setIsHighlighted(true));

        // if not then highlight first matching label
        if (!hasHighlightedLabel()) {
            labelMatches.stream()
                    .findFirst()
                    .ifPresent(label -> label.setIsHighlighted(true));
        }
    }

    public boolean hasHighlightedLabel() {
        return matchingLabels.stream()
                .filter(PickerLabel::isHighlighted)
                .findAny()
                .isPresent();
    }

    private String getHighlightedLabelName() {
        return getHighlightedLabel().get().getActualName();
    }

    private void setHighlightedLabel(PickerLabel label) {
        label.setIsHighlighted(true);
        updatePossibleLabel(label.getActualName());
    }

    private Optional<PickerLabel> getHighlightedLabel() {
        return matchingLabels.stream()
                .filter(PickerLabel::isHighlighted)
                .findAny();
    }

    @SuppressWarnings("unused")
    private void ______BOILERPLATE______() {}

    private boolean isAnActiveLabel(String name) {
        return activeLabels.get(name);
    }

    public Map<String, Boolean> getActiveLabels() {
        return activeLabels;
    }

    private boolean isAnExistingLabel(String name) {
        return issue.getLabels().contains(name);
    }

    private List<PickerLabel> getExistingLabels() {
        return modificationLabels.stream()
                .filter(label -> issue.getLabels().contains(label.getActualName()))
                .collect(Collectors.toList());
    }

    private List<PickerLabel> getAddedLabels() {
        return modificationLabels.stream()
                .filter(label -> !issue.getLabels().contains(label.getActualName()))
                .collect(Collectors.toList());
    }

}
