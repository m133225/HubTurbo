package ui;

import handler.IssueDetailsContentHandler;
import model.TurboComment;
import model.TurboIssue;
import ui.IssueDetailsDisplay.DisplayType;
import javafx.geometry.Insets;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

public class DetailsPanel extends VBox {
	public static int COMMENTS_CELL_HEIGHT = 200;
	public static int COMMENTS_PADDING = 5;
	
	private ListView<TurboComment> listView;
	private IssueDetailsContentHandler handler;
	private TurboIssue issue;
	private DisplayType displayType;
	
	public DetailsPanel(TurboIssue issue, IssueDetailsContentHandler handler, DisplayType displayType){
		this.issue = issue;
		this.listView = new ListView<TurboComment>();
		this.handler = handler;
		this.displayType = displayType;
		this.setPadding(new Insets(COMMENTS_PADDING));
		loadItems();
		this.setFillWidth(true);
	}
	
	
	private Callback<ListView<TurboComment>, ListCell<TurboComment>> commentCellFactory(){
		Callback<ListView<TurboComment>, ListCell<TurboComment>> factory = new Callback<ListView<TurboComment>, ListCell<TurboComment>>() {
			@Override
			public ListCell<TurboComment> call(ListView<TurboComment> list) {
				return new DetailsCell(issue, displayType);
			}
		};
		return factory;
	}
	
	private void loadItems() {
		if(displayType == DisplayType.COMMENTS){
			loadNewCommentsBox();
		}
		setListItems();
	}

	
	private void loadNewCommentsBox(){
		CommentsEditBox box = new CommentsEditBox(handler);
		box.setPrefHeight(COMMENTS_CELL_HEIGHT);
		getChildren().add(box);
		setMargin(box, new Insets(COMMENTS_PADDING));
	}
	
	private void setListItems(){
		listView.setCellFactory(commentCellFactory());
		if(displayType == DisplayType.COMMENTS){
			listView.setItems(handler.getComments());
		}else{
			listView.setItems(handler.getIssueHistory());
		}
	}
}
