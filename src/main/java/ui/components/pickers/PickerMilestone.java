package ui.components.pickers;

import backend.resource.TurboMilestone;
import com.sun.javafx.tk.FontLoader;
import com.sun.javafx.tk.Toolkit;
import javafx.scene.Node;
import javafx.scene.control.Label;

public class PickerMilestone extends TurboMilestone implements Comparable<PickerMilestone> {
    boolean isSelected = false;
    boolean isHighlighted = false;
    boolean isFaded = false;
    boolean isExisting = false;

    public PickerMilestone(TurboMilestone milestone) {
        super(milestone.getRepoId(), milestone.getId(), milestone.getTitle());
        setDueDate(milestone.getDueDate());
        setDescription(milestone.getDescription() == null ? "" : milestone.getDescription());
        setOpen(milestone.isOpen());
        setOpenIssues(milestone.getOpenIssues());
        setClosedIssues(milestone.getClosedIssues());
    }

    public PickerMilestone(PickerMilestone milestone) {
        super(milestone.getRepoId(), milestone.getId(), milestone.getTitle());
        setDueDate(milestone.getDueDate());
        setDescription(milestone.getDescription() == null ? "" : milestone.getDescription());
        setOpen(milestone.isOpen());
        setOpenIssues(milestone.getOpenIssues());
        setClosedIssues(milestone.getClosedIssues());
        setFaded(milestone.isFaded());
        setHighlighted(milestone.isHighlighted());
        setSelected(milestone.isSelected());
        setExisting(milestone.isExisting());
    }

    public Node getNode() {
        Label milestone = new Label(getTitle());
        FontLoader fontLoader = Toolkit.getToolkit().getFontLoader();
        double width = fontLoader.computeStringWidth(milestone.getText(), milestone.getFont());
        milestone.setPrefWidth(width + 30);
        milestone.getStyleClass().add("labels");
        milestone.setStyle("-fx-background-color: yellow;");

        if (isSelected) {
            milestone.setText(milestone.getText() + " ✓");
        }

        if (isHighlighted) {
            milestone.setStyle(milestone.getStyle() + "-fx-border-color: black;");
        }

        if (isFaded) {
            milestone.setStyle(milestone.getStyle() + "-fx-opacity: 40%;");
        }

        return milestone;
    }

    public Node getNewlyAssignedMilestoneNode(boolean hasSuggestion) {
        Label milestone = new Label(getTitle());
        FontLoader fontLoader = Toolkit.getToolkit().getFontLoader();
        double width = fontLoader.computeStringWidth(milestone.getText(), milestone.getFont());
        milestone.setPrefWidth(width + 30);
        milestone.getStyleClass().add("labels");
        milestone.setStyle("-fx-background-color: yellow;");

        if (hasSuggestion) {
            milestone.setStyle(milestone.getStyle() + "-fx-opacity: 40%;");
            if (isSelected) {
                milestone.getStyleClass().add("labels-removed"); // add strikethrough
            }
        }

        if (isSelected) {
            milestone.setStyle(milestone.getStyle() + "-fx-border-color: black;");
        }

        return milestone;
    }

    public Node getExistingMilestoneNode(boolean hasSuggestion) {
        Label milestone = new Label(getTitle());
        FontLoader fontLoader = Toolkit.getToolkit().getFontLoader();
        double width = fontLoader.computeStringWidth(milestone.getText(), milestone.getFont());
        milestone.setPrefWidth(width + 30);
        milestone.getStyleClass().add("labels");
        milestone.setStyle("-fx-background-color: yellow;");

        if (isSelected && (hasSuggestion || isHighlighted)) {
            milestone.setStyle(milestone.getStyle() + "-fx-opacity: 40%;");
        }

        if (!isExisting || !isSelected || hasSuggestion || isHighlighted) {
            milestone.getStyleClass().add("labels-removed"); // add strikethrough
        }

        return milestone;
    }

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    public boolean isSelected() {
        return this.isSelected;
    }

    public void setHighlighted(boolean isHighlighted) {
        this.isHighlighted = isHighlighted;
    }

    public boolean isHighlighted() {
        return this.isHighlighted;
    }

    public void setFaded(boolean isFaded) {
        this.isFaded = isFaded;
    }

    public boolean isFaded() {
        return this.isFaded;
    }
    public void setExisting(boolean isExisting) {
        this.isExisting = isExisting;
    }

    public boolean isExisting() {
        return this.isExisting;
    }

    /**
     * This treats null milestones with no set due date to be "larger than"
     * those which have due dates
     *
     * @param milestone
     * @return
     */
    @Override
    public int compareTo(PickerMilestone milestone) {
        if (this.getDueDate().equals(milestone.getDueDate())) return 0;
        if (!this.getDueDate().isPresent()) return 1;
        if (!milestone.getDueDate().isPresent()) return -1;

        return this.getDueDate().get()
                .isAfter(milestone.getDueDate().get()) ? 1 : -1;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
