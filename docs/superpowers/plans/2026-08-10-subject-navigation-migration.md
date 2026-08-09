# Subject Navigation Migration Implementation Plan

**Goal:** Make the subject list and subject detail screens sibling destinations managed by the main NavController instead of nested fragments inside SubjectFragment.

**Architecture:** `nav_subject` hosts `SubjectListFragment`; `nav_subject_detail` hosts `SubjectDetailFragment` with a `subject_id` argument. List/detail transitions use NavController actions, while MainActivity resolves default-entry and shortcut intents directly to the appropriate destination.

**Tech Stack:** Android Java, AndroidX Navigation, existing Fragment menus and SubjectEntryManager.

## Tasks

1. Add the `nav_subject_detail` destination and the list-to-detail action to `mobile_navigation.xml`; change `nav_subject` to `SubjectListFragment`.
2. Update `SubjectListFragment` to navigate with `subject_id` and own the subject menu directly.
3. Update `SubjectDetailFragment` to read the navigation argument, use `navigateUp()` for the list action, and remove the parent callback contract.
4. Update MainActivity's default-entry and shortcut navigation to target list/detail destinations without delayed child-fragment lookup.
5. Remove the obsolete SubjectFragment routing implementation and validate with `assembleDebug` and `testDebugUnitTest`.
