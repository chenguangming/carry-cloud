package com.photons.carrycloud.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.photons.carrycloud.databinding.FragmentGridHomeBinding

data class GridItem(val text: String, val isAddButton: Boolean)

class HomeGridFragment : Fragment() {

    private var _binding: FragmentGridHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: GridAdapter
    private val items = mutableListOf<GridItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGridHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initView()

        return root
    }

    private fun initView () {

        // 初始化数据
        repeat(4) { i ->
            items.add(GridItem("Item ${i + 1}", false))
        }
        items.add(GridItem("", true)) // 加号项

        // 初始化 GridView
        binding.gridView.numColumns = calculateSpanCount()

        // 设置 Adapter
        adapter = GridAdapter(requireContext(), items) { position ->
            if (position == items.size - 1 && items[position].isAddButton) {
                // 点击加号，添加新项
                items.add(items.size - 1, GridItem("Item ${items.size}", false))
                adapter.notifyDataSetChanged()
            }
        }
        binding.gridView.adapter = adapter
    }

    // 根据屏幕宽度计算列数
    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val minItemWidthDp = 100f // 每格最小宽度（单位：dp）
        return minOf(6, (screenWidthDp / minItemWidthDp).toInt()) // 至少3列
    }
}