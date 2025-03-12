package com.photons.carrycloud.ui.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.INPUT_METHOD_SERVICE
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.photons.carrycloud.App
import com.photons.carrycloud.R
import com.photons.carrycloud.databinding.FragmentNotificationsBinding
import com.photons.carrycloud.ui.notifications.BubbleData.Companion.TYPE_CLIENT
import com.photons.carrycloud.ui.notifications.BubbleData.Companion.TYPE_SERVER
import com.photons.carrycloud.ui.notifications.BubbleData.Companion.TYPE_SYSTEM
import java.util.*


class NotificationsFragment : Fragment() {
    private lateinit var recyclerViewAdapter: MessageListViewAdapter

    private var _binding: FragmentNotificationsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var imm: InputMethodManager? = null
    private var cm: ClipboardManager? = null

    companion object {
        private val SDF_PATTERN = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val gson = Gson()
    }

    inner class MessageListViewAdapter(history : ArrayList<BubbleData>) : RecyclerView.Adapter<BubbleViewHolder>() {
        private val contents: ArrayList<BubbleData> = arrayListOf()
        private var size = 0

        init {
            if (history.isNotEmpty()) {
                contents.addAll(history)
            }
            size = contents.size
        }

        fun addContent(data: BubbleData) {
            if (contents.isNotEmpty() && contents.last().id == data.id) {
                return
            }

            contents.add(data)
            size++
            notifyItemInserted(size - 1)
            binding.recyclerView.smoothScrollToPosition(size - 1) //自动滚动到底部
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BubbleViewHolder {
            return when (viewType) {
                TYPE_SERVER -> BubbleViewHolder(
                    layoutInflater.inflate(
                        R.layout.bubble_server,
                        parent,
                        false
                    )
                )
                TYPE_CLIENT -> BubbleViewHolder(
                    layoutInflater.inflate(
                        R.layout.bubble_client,
                        parent,
                        false
                    )
                )
                else -> BubbleViewHolder(
                    layoutInflater.inflate(
                        R.layout.bubble_system,
                        parent,
                        false
                    )
                )
            }
        }

        override fun getItemCount(): Int = size

        override fun onBindViewHolder(holder: BubbleViewHolder, position: Int) {
            holder.applyData(contents[position])
        }

        override fun getItemViewType(position: Int): Int {
            return when (contents[position].type) {
                TYPE_SERVER -> TYPE_SERVER
                TYPE_CLIENT -> TYPE_CLIENT
                else -> TYPE_SYSTEM
            }
        }
    }

    inner class BubbleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val time: AppCompatTextView? = itemView.findViewById(R.id.time)
        private val content: AppCompatTextView? = itemView.findViewById(R.id.content)

        fun applyData(data: BubbleData) {
            time?.text = SDF_PATTERN.format(Date(data.id))
            content?.text = data.msg
            itemView.setOnClickListener {
                cm?.setPrimaryClip(ClipData.newPlainText("CarryCloud", data.msg))
                App.instance.toast(getString(R.string.copied))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewModel = ViewModelProvider(this)[NotificationsViewModel::class.java]
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)

        imm = context?.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        cm = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        App.instance.startWsServer()
        App.instance.bindViewModel(viewModel)

        recyclerViewAdapter = MessageListViewAdapter(MyWebSocketServer.allMessage)
        binding.recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.recyclerView.adapter = recyclerViewAdapter

        viewModel.receiveMessage.observe(this) {
            recyclerViewAdapter.addContent(it)
        }

        binding.sendButton.setOnClickListener {
            binding.editText.apply {
                text?.toString()?.let { text ->
                    if (TextUtils.isEmpty(text)) {
                        return@let
                    }

                    val msg = BubbleData(TYPE_CLIENT, text)
                    MyWebSocketServer.allMessage.add(msg)
                    viewModel.sendStringData.postValue(gson.toJson(msg))
                    recyclerViewAdapter.addContent(msg)
                }

                setText("")
                hideKeyboard(this)
            }
        }

        return binding.root
    }

    private fun hideKeyboard(view: View) {
        imm?.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        view.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        App.instance.unbindViewModel()
        _binding = null
    }
}