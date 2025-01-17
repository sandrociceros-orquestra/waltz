package org.finos.waltz.test_common.playwright;

/**
 * List of the waltz ui sections and their id's.
 */
public enum Section {
    APP_SURVEYS(17),
    ASSESSMENTS(200),
    BOOKMARKS(5),
    MEASURABLE_RATINGS(15);


    private int id;

    Section(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
