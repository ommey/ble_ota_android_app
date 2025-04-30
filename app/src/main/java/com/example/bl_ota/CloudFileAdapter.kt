package com.example.bl_ota

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CloudFileAdapter(
    private val files: List<CloudFile>,
    private val onItemClick: (CloudFile) -> Unit
) : RecyclerView.Adapter<CloudFileAdapter.FileViewHolder>() {

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.fileNameText)
        val meta: TextView = view.findViewById<TextView>(R.id.fileMetaText)

        fun bind(file: CloudFile) {
            name.text = file.name
            val context = itemView.context
            meta.text = context.getString(R.string.firmware_meta, file.version, file.uploadDate)
            itemView.setOnClickListener { onItemClick(file) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cloud_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size
}
