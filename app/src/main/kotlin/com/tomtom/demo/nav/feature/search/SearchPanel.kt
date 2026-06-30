package com.tomtom.demo.nav.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomtom.demo.nav.R

private typealias SearchItem = SearchViewModel.SearchItem

/**
 * ［feature:search］搜索卡片（Compose 体系，取代原 search_card 布局 + SearchResultAdapter）。
 *
 * 输入框聚焦且为空时展示收藏/历史（saved）；有搜索结果时展示结果（results）。
 * 列表项点选即清空焦点与输入并回调 [onResultClick]（由上层执行算路）。
 */
@Composable
fun SearchPanel(
    results: List<SearchItem>,
    saved: List<SearchItem>,
    onSearch: (String) -> Unit,
    onResultClick: (SearchItem) -> Unit,
    onFavorite: (SearchItem) -> Unit,
    onClearResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val listToShow = when {
        results.isNotEmpty() -> results
        focused && query.isBlank() -> saved
        else -> emptyList()
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShapeCompat,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) onClearResults()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch(query)
                        focusManager.clearFocus()
                    },
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )

            if (listToShow.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(listToShow, key = { it.position }) { item ->
                        SearchResultRow(
                            item = item,
                            onClick = {
                                onResultClick(item)
                                query = ""
                                focusManager.clearFocus()
                            },
                            onFavorite = { onFavorite(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchItem,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                text = item.address,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        // 已保存条目（收藏/历史）不再显示收藏按钮
        if (!item.isSaved) {
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.favorite_add),
                )
            }
        }
    }
}

private val RoundedCornerShapeCompat = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
