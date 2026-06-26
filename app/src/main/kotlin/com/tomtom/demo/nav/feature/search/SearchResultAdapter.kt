package com.tomtom.demo.nav.feature.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tomtom.demo.nav.databinding.ItemSearchResultBinding

/** ［feature:search · WS2］搜索结果 / 收藏历史列表。 */
class SearchResultAdapter(
    private val onClick: (SearchViewModel.SearchItem) -> Unit,
    private val onFavorite: (SearchViewModel.SearchItem) -> Unit,
) : ListAdapter<SearchViewModel.SearchItem, SearchResultAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.resultName.text = item.name
        holder.binding.resultAddress.text = item.address
        holder.binding.root.setOnClickListener { onClick(item) }
        // 已保存条目（收藏/历史列表）不再显示收藏按钮
        holder.binding.favoriteButton.isVisible = !item.isSaved
        holder.binding.favoriteButton.setOnClickListener { onFavorite(item) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchViewModel.SearchItem>() {
            override fun areItemsTheSame(
                oldItem: SearchViewModel.SearchItem,
                newItem: SearchViewModel.SearchItem,
            ) = oldItem.position == newItem.position

            override fun areContentsTheSame(
                oldItem: SearchViewModel.SearchItem,
                newItem: SearchViewModel.SearchItem,
            ) = oldItem == newItem
        }
    }
}
