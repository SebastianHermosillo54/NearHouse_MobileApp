package com.equipo4.nearhome.ui.subscription

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.equipo4.nearhome.data.session.CurrentUserStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChoosePlanUiState(
    val planes: List<SubscriptionPlan> = emptyList(),
    val cargando: Boolean = true,
    /** true mientras se reintenta desde la pantalla "sin conexión". */
    val reintentando: Boolean = false,
    val errorCarga: Boolean = false,
    val errorCargaMensaje: String? = null,
    val pruebaDisponible: Boolean = false,
    /** Plan vigente y sin cancelar: sale como "Plan actual" y se puede cancelar. */
    val planActualId: Int? = null,
    /** Fecha ISO en que vence el plan actual (para el aviso de cancelar). */
    val planActualVence: String? = null,
    /** Plan cancelado que sigue vigente: ya no sale como actual; se avisa hasta cuándo se ven los anuncios. */
    val planCanceladoNombre: String? = null,
    val planCanceladoVence: String? = null,
    /** Fecha ISO hasta la que se ven los anuncios, justo después de cancelar (muestra la confirmación). */
    val cancelacionHecha: String? = null,
    val selectedPlanId: Int? = null,
    val guardando: Boolean = false,
    val errorAccion: String? = null,
    val errorCodigo: String? = null
)

class ChoosePlanViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SubscriptionRepository(application)

    private val _uiState = MutableStateFlow(ChoosePlanUiState())
    val uiState: StateFlow<ChoosePlanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            CurrentUserStore.user.collect { u ->
                val plan = u?.plan
                val cancelado = plan?.takeIf { it.vigente && it.cancelado }
                _uiState.update {
                    it.copy(
                        pruebaDisponible = u?.pruebaDisponible == true,
                        planActualId = plan?.takeIf { p -> p.activoSinCancelar }?.id,
                        planActualVence = plan?.takeIf { p -> p.activoSinCancelar }?.fechaVencimiento,
                        planCanceladoNombre = cancelado?.nombre,
                        planCanceladoVence = cancelado?.fechaVencimiento
                    )
                }
            }
        }
        cargarPlanes()
    }

    fun cargarPlanes() {
        viewModelScope.launch {
            // Si se reintenta desde "sin conexión", la pantalla se queda igual hasta tener resultado
            val retrying = _uiState.value.errorCarga
            _uiState.update { it.copy(cargando = !retrying, reintentando = retrying) }
            when (val r = repo.obtenerPlanes()) {
                is Resultado.Ok -> {
                    val planes = r.valor.map { it.toModel() }
                    SubscriptionPlanCache.plans = planes
                    _uiState.update { state ->
                        val newSelected = state.selectedPlanId?.let { id -> if (planes.any { it.id == id }) id else null }
                        state.copy(planes = planes, cargando = false, reintentando = false, errorCarga = false, errorCargaMensaje = null, selectedPlanId = newSelected)
                    }
                }
                is Resultado.Error -> _uiState.update { it.copy(cargando = false, reintentando = false, errorCarga = true, errorCargaMensaje = r.mensaje) }
            }
        }
    }

    fun seleccionarPlan(planId: Int) {
        _uiState.update { it.copy(selectedPlanId = planId) }
    }

    fun contratar(plan: SubscriptionPlan, onDone: () -> Unit) {
        if (_uiState.value.guardando) return
        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorAccion = null, errorCodigo = null) }
            when (val r = repo.contratar(plan.id)) {
                is Resultado.Ok -> {
                    _uiState.update { it.copy(guardando = false) }
                    onDone()
                }
                is Resultado.Error -> _uiState.update {
                    it.copy(guardando = false, errorAccion = r.mensaje, errorCodigo = r.codigo)
                }
            }
        }
    }

    fun cancelarPlan(onDone: () -> Unit = {}) {
        if (_uiState.value.guardando) return
        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorAccion = null, errorCodigo = null) }
            when (val r = repo.cancelarPlan()) {
                is Resultado.Ok -> {
                    _uiState.update {
                        it.copy(guardando = false, cancelacionHecha = r.valor.plan?.fechaVencimiento)
                    }
                    onDone()
                }
                is Resultado.Error -> _uiState.update {
                    it.copy(guardando = false, errorAccion = r.mensaje, errorCodigo = r.codigo)
                }
            }
        }
    }

    fun limpiarError() {
        _uiState.update { it.copy(errorAccion = null, errorCodigo = null) }
    }

    fun cerrarConfirmacionCancelacion() {
        _uiState.update { it.copy(cancelacionHecha = null) }
    }
}
