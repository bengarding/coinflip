package com.helsinkiwizard.cointoss.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.helsinkiwizard.cointoss.data.Repository
import com.helsinkiwizard.cointoss.ui.model.CreateCoinModel
import com.helsinkiwizard.cointoss.ui.model.CustomCoinUiModel
import com.helsinkiwizard.core.CoreConstants.EMPTY_STRING
import com.helsinkiwizard.core.coin.CoinSide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateCoinViewModel @Inject constructor(
    private val repository: Repository
) : AbstractViewModel() {

    private val model = CreateCoinModel(
        selectedCoin = repository.getSelectedCustomCoin(),
        customCoins = repository.getCustomCoins()
    )

    init {
        mutableUiStateFlow.value = UiState.ShowContent(CreateCoinContent.LoadingComplete(model))
    }

    fun setBitmap(bitmap: Bitmap, coinSide: CoinSide) {
        if (coinSide == CoinSide.HEADS) {
            model.headsError = false
            model.headsBitmap = bitmap
        } else {
            model.tailsError = false
            model.tailsBitmap = bitmap
        }
    }

    fun onCoinSideClicked(coinSide: CoinSide) {
        mutableDialogStateFlow.value = DialogState.ShowContent(CreateCoinDialogs.MediaPicker(coinSide))
    }

    fun saveCoin(storeBitmap: (Bitmap?) -> Uri?) {
        model.name.validate()
        model.headsError = model.headsBitmap == null
        model.tailsError = model.tailsBitmap == null

        if (model.headsError || model.tailsError || model.name.isError) {
            mutableDialogStateFlow.value = DialogState.ShowContent(CreateCoinDialogs.MissingImages)
            return
        }

        val headsUri = storeBitmap(model.headsBitmap)
        val tailsUri = storeBitmap(model.tailsBitmap)

        if (headsUri != null && tailsUri != null) {
            viewModelScope.launch {
                if (model.editingCoin != null) {
                    repository.updateCustomCoin(headsUri, tailsUri, model.name.value, model.editingCoin!!.id)
                    mutableDialogStateFlow.value = DialogState.ShowContent(
                        CreateCoinDialogs.DeleteCoin(model.editingCoin!!.headsUri, model.editingCoin!!.tailsUri)
                    )
                } else {
                    repository.storeCustomCoin(headsUri, tailsUri, model.name.value)
                }
                clear()
            }
        } else {
            mutableDialogStateFlow.value = DialogState.ShowContent(CreateCoinDialogs.SaveError)
        }
    }

    fun clear() {
        model.headsError = false
        model.tailsError = false
        model.headsBitmap = null
        model.tailsBitmap = null
        model.name.value = EMPTY_STRING
        model.editingCoin = null
    }

    fun onNameChange(name: String) {
        with(model.name) {
            value = name
            if (isError) {
                validate()
            }
        }
    }

    fun onEditClicked(
        coin: CustomCoinUiModel,
        uriToBitmap: (Uri) -> Bitmap?
    ) {
        clear()
        model.headsBitmap = uriToBitmap(coin.headsUri)
        model.tailsBitmap = uriToBitmap(coin.tailsUri)
        model.name.value = coin.name
        model.editingCoin = coin
    }
}

sealed interface CreateCoinContent : BaseType {
    data class LoadingComplete(val model: CreateCoinModel) : CreateCoinContent
}

sealed interface CreateCoinDialogs : BaseDialogType {
    data class MediaPicker(val coinSide: CoinSide) : CreateCoinDialogs
    data object MissingImages : CreateCoinDialogs
    data object SaveError : CreateCoinDialogs
    data class DeleteCoin(val headsUri: Uri, val tailsUri: Uri) : CreateCoinDialogs
}
