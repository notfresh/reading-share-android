// 1. 生命周期方法
onCreateView()
onCreate()
onCreateOptionsMenu()
onOptionsItemSelected()
onDestroyView()

// 2. 核心标签管理方法
loadTags()
addTagView()
setTagStyle()
updateTagSelection()
handleTagClick()
showTagOptionsDialog()
confirmDeleteTag()
publishTagToWebsite()

// 3. 内容筛选和更新方法
updateContentBySelectedTags()
updateTitle()
updateLinksList()

// 4. 状态管理方法
saveSelections()
restoreSelections()
clearSavedSelections()
loadHighlightedTags()
toggleTagHighlight()

// 5. 选择模式相关方法
toggleSelectionMode()
selectAllItems()
onEnterSelectionMode()

// 6. 分享功能方法
shareAsText()
shareAsFile()
exportAndShareFile()

// 7. UI交互方法
toggleTagsExpansion()
checkTagsVisibility()
refreshTags()

// 8. 工具方法
setTagViewId()
getTagNameFromView()
isNoTagView()

// 9. 接口实现方法
onDeleteLink()
onUpdateLink()
addTagToLink()
updateLinkTags()
onPinStatusChanged()