package com.allsoftdroid.audiobook.feature_listen_later_ui.presentation

import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.allsoftdroid.audiobook.feature_listen_later_ui.R
import com.allsoftdroid.audiobook.feature_listen_later_ui.data.model.ListenLaterItemDomainModel
import com.allsoftdroid.audiobook.feature_listen_later_ui.databinding.FragmentListenLaterLayoutBinding
import com.allsoftdroid.audiobook.feature_listen_later_ui.di.FeatureListenLaterModule
import com.allsoftdroid.audiobook.feature_listen_later_ui.domain.Empty
import com.allsoftdroid.audiobook.feature_listen_later_ui.domain.Started
import com.allsoftdroid.audiobook.feature_listen_later_ui.domain.Success
import com.allsoftdroid.audiobook.feature_listen_later_ui.domain.contracts.ExportFileContract
import com.allsoftdroid.audiobook.feature_listen_later_ui.domain.contracts.ImportFileContract
import com.allsoftdroid.audiobook.feature_listen_later_ui.presentation.recyclerView.ItemClickedListener
import com.allsoftdroid.audiobook.feature_listen_later_ui.presentation.recyclerView.ListenLaterAdapter
import com.allsoftdroid.audiobook.feature_listen_later_ui.presentation.recyclerView.OptionsClickedListener
import com.allsoftdroid.audiobook.feature_listen_later_ui.utils.SortType
import com.allsoftdroid.common.base.fragment.BaseUIFragment
import com.allsoftdroid.common.base.network.StoreUtils
import com.allsoftdroid.common.base.utils.ShareUtils
import com.allsoftdroid.common.base.utils.StoragePermissionHandler
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent
import java.io.FileNotFoundException
import java.io.IOException


class ListenLaterFragment : BaseUIFragment(),KoinComponent {
    private val listenLaterViewModel: ListenLaterViewModel by inject()

    private lateinit var bindingRef:FragmentListenLaterLayoutBinding
    private lateinit var importFileContract: ActivityResultLauncher<Int>
    private lateinit var exportFileContract: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FeatureListenLaterModule.injectFeature()
        initializeContracts()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val dataBinding:FragmentListenLaterLayoutBinding = inflateLayout(inflater,
            R.layout.fragment_listen_later_layout,container,false)

        dataBinding.lifecycleOwner = viewLifecycleOwner
        dataBinding.viewModel = listenLaterViewModel

        //val audio book adapter
        val listenLaterAdapter = ListenLaterAdapter(
            requireContext(),

            ItemClickedListener {bookId->
                //Navigate to display page
                val bundle = bundleOf("bookId" to bookId)

                this.findNavController()
                    .navigate(R.id.action_ListenLaterFragment_to_AudioBookDetailsFragment,bundle)
            },

            OptionsClickedListener(
                onRemove = {
                    listenLaterViewModel.removeItem(it.identifier)
                },

                onShare = {item->
                    this.activity?.let {parent->
                        ShareUtils.share(
                            context = parent,
                            subject = "${item.title} on AudioBook",
                            txt = "Listen ${item.title} written by '${item.author}' on AudioBook App," +
                                    " Start Listening: ${StoreUtils.getStoreUrl(requireActivity())}"
                        )
                    }
                }
            )
        )

        //attach adapter to recycler view
        dataBinding.recyclerViewBooks.adapter = listenLaterAdapter

        //recycler view layout manager
        dataBinding.recyclerViewBooks.apply {
            layoutManager = LinearLayoutManager(context)
        }

        dataBinding.toolbarBackArrow.setOnClickListener {
            onBackPressed()
        }

        dataBinding.sortList.setOnClickListener {
            showPopMenu(it)
        }

        listenLaterViewModel.requestStatus.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.let { request ->
                when(request){
                    is Started -> {
                        loading()
                    }

                    is Empty -> {
                        noDataUI()
                    }

                    is Success -> {
                        if (request.list.isEmpty()){
                            noDataUI()
                        }else
                        {
                            dataAvailable(request.list)
                            listenLaterAdapter.submitList(request.list)
                        }
                    }
                }
            }
        })

        listenLaterViewModel.notification.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
            }
        })

        bindingRef = dataBinding

        registerForContextMenu(dataBinding.toolbarExportImport)
        dataBinding.toolbarExportImport.setOnClickListener {
            it.showContextMenu()
        }

        return dataBinding.root
    }

    private fun export() {
        if (StoragePermissionHandler.isPermissionGranted(requireActivity())){
            if (listenLaterViewModel.listenLaterData.isNotEmpty()){
                val argument = System.currentTimeMillis().toString()
                exportFileContract.launch(getString(R.string.export_filename_format,argument))
            }else{
                Toast.makeText(requireActivity(),getString(R.string.no_data_export_message),Toast.LENGTH_SHORT).show()
            }
        }else{
            StoragePermissionHandler.requestPermission(requireActivity())
        }
    }

    private fun initializeContracts(){
        importFileContract = registerForActivityResult(ImportFileContract()){ resultUri ->
            if (resultUri == null){
                Toast.makeText(requireActivity(),getString(R.string.import_cancelled_message),Toast.LENGTH_SHORT).show()
            }else{
                readFileContent(resultUri)
            }
        }

        exportFileContract = registerForActivityResult(ExportFileContract()){ resultUri ->
            if (resultUri == null){
                Toast.makeText(requireActivity(),getString(R.string.export_cancelled_message),Toast.LENGTH_SHORT).show()
            }else{
                writeFileContent(resultUri)
            }
        }
    }

    private fun import() {
        if (StoragePermissionHandler.isPermissionGranted(requireActivity())){
            importFileContract.launch(null)
        }else{
            StoragePermissionHandler.requestPermission(requireActivity())
        }
    }

    private fun readFileContent(currentUri: Uri) {
        try {
            val pfd: ParcelFileDescriptor? = requireActivity().contentResolver.openFileDescriptor(currentUri, "r")
            pfd?.let {parcelFileDesc ->
                listenLaterViewModel.import(parcelFileDesc)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun writeFileContent(currentUri: Uri) {
        try {
            val pfd: ParcelFileDescriptor? = requireActivity().contentResolver.openFileDescriptor(currentUri, "w")
            pfd?.let {parcelFileDesc ->
                listenLaterViewModel.export(parcelFileDesc)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun showPopMenu(view: View?) {
        view?.let {
            val popUp = PopupMenu(requireContext(),it)
            popUp.inflate(R.menu.sort_options_menu)

            popUp.setOnMenuItemClickListener {item ->

                listenLaterViewModel.setCurrentShortType(when(item.itemId){
                    R.id.menu_options_sort_latest_first -> {
                        SortType.LatestFirst
                    }

                    R.id.menu_options_sort_oldest_first -> {
                        SortType.OldestFirst
                    }

                    R.id.menu_options_sort_shortest_first->{
                        SortType.ShortestFirst
                    }

                    else -> SortType.LatestFirst
                })

                return@setOnMenuItemClickListener false
            }
            popUp.show()
        }
    }

    private fun noDataUI() {
        removeLoading()
    }

    private fun dataAvailable(list: List<ListenLaterItemDomainModel>) {

        bindingRef.bookStatsCount.apply {
            this.text = context.getString(R.string.book_count,list.size)
        }

        bindingRef.loadingProgressbar.apply {
            this.visibility = View.GONE
        }

        bindingRef.recyclerViewBooks.apply {
            this.visibility = View.VISIBLE
        }

        bindingRef.bookStats.apply {
            this.visibility = View.VISIBLE
        }
    }

    private fun removeLoading(){
        bindingRef.loadingProgressbar.apply {
            this.visibility = View.GONE
        }

        bindingRef.noData.apply {
            this.visibility = View.VISIBLE
        }
    }

    private fun loading(){
        bindingRef.loadingProgressbar.apply {
            this.visibility = View.VISIBLE
        }

        bindingRef.noData.apply {
            this.visibility = View.GONE
        }

        bindingRef.recyclerViewBooks.apply {
            this.visibility = View.GONE
        }

        bindingRef.bookStats.apply {
            this.visibility = View.GONE
        }
    }

    override fun handleBackPressEvent(callback: OnBackPressedCallback) {
        callback.isEnabled = false
        requireActivity().onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        listenLaterViewModel.loadList()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = MenuInflater(v.context)

        inflater.inflate(R.menu.backup_restore_menu,menu)
        menu.setHeaderTitle(getString(R.string.backup_restore_header))
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {

        return when(item.itemId){
            R.id.export_data -> {
                export()
                true
            }
            R.id.import_data -> {
                import()
                true
            }
            else -> {
                false
            }
        }
    }
}