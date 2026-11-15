package com.equipo4.nearhome.ui.admin.listings

import com.equipo4.nearhome.domain.model.AdminListing
import com.equipo4.nearhome.domain.model.Advertiser
import com.equipo4.nearhome.domain.model.ListingAction
import com.equipo4.nearhome.domain.model.ListingStatus
import com.equipo4.nearhome.domain.model.ListingType
import com.equipo4.nearhome.domain.model.PropertyType
import com.equipo4.nearhome.domain.model.StatusChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la lógica de la lista de anuncios del administrador (filtros, búsqueda, orden y botones).
 * Antes usaban AdminListingRepositoryFake, que se quitó al conectar la pantalla con la API;
 * ahora los datos de prueba se arman aquí mismo.
 */
class AdminListingsLogicTest {

    private val mariana = Advertiser(id = 1, name = "Mariana López Hernández", email = "mariana@correo.com", verified = true)
    private val anthony = Advertiser(id = 2, name = "Anthony Edward Stark", email = "anthony@correo.com", verified = true)
    private val carlos = Advertiser(id = 3, name = "Carlos Méndez Ortega", email = "carlos@correo.com", verified = false)

    private fun anuncio(
        id: String,
        titulo: String,
        tipo: PropertyType,
        modalidad: ListingType,
        anunciante: Advertiser,
        registrado: Long,
        estado: ListingStatus = ListingStatus.PENDIENTE
    ) = AdminListing(
        id = id,
        title = titulo,
        type = tipo,
        listingType = modalidad,
        price = 1000.0,
        location = "Manzanillo, Colima",
        areaSqM = 100,
        description = "Descripción de prueba",
        images = emptyList(),
        advertiser = anunciante,
        registeredAt = registrado,
        status = estado,
        history = listOf(StatusChange(null, ListingStatus.PENDIENTE, registrado, anunciante.name))
    )

    private val datos = listOf(
        anuncio("NH-8421", "Departamento amueblado cerca del centro", PropertyType.DEPARTAMENTO, ListingType.RENTA, mariana, 5_000),
        anuncio("NH-8420", "Casa residencial en Real Vista", PropertyType.CASA, ListingType.VENTA, anthony, 4_000),
        anuncio("NH-8419", "Terreno plano en Salagua", PropertyType.TERRENO, ListingType.VENTA, carlos, 3_000),
        anuncio("NH-8410", "Departamento con vista al mar", PropertyType.DEPARTAMENTO, ListingType.RENTA, anthony, 2_000, ListingStatus.ACTIVO),
        anuncio("NH-8402", "Casa en venta amueblada", PropertyType.CASA, ListingType.VENTA, carlos, 1_000, ListingStatus.RECHAZADO),
        anuncio("NH-8395", "Departamento económico", PropertyType.DEPARTAMENTO, ListingType.RENTA, mariana, 500, ListingStatus.DESACTIVADO)
    )

    private val sinFiltro = ListingFilters(statuses = emptySet())

    @Test fun colaPorDefectoSoloPendientesDelMasNuevoAlMasViejo() {
        val res = applyListingFilters(datos, "", ListingFilters.Queue)
        assertTrue(res.all { it.status == ListingStatus.PENDIENTE })
        assertEquals(res.sortedByDescending { it.registeredAt }, res)
        assertEquals(datos.count { it.status == ListingStatus.PENDIENTE }, res.size)
    }

    @Test fun buscaPorTituloIdYAnunciante() {
        assertEquals(listOf("NH-8421"), applyListingFilters(datos, "8421", sinFiltro).map { it.id })
        assertEquals(listOf("NH-8421"), applyListingFilters(datos, "NH-8421", sinFiltro).map { it.id })
        assertEquals(listOf("NH-8419"), applyListingFilters(datos, "salagua", sinFiltro).map { it.id })
        val porAnunciante = applyListingFilters(datos, "anthony", sinFiltro)
        assertTrue(porAnunciante.isNotEmpty() && porAnunciante.all { it.advertiser.name.contains("Anthony") })
    }

    @Test fun busquedaIgnoraMayusculasYAcentos() {
        val conAcentos = applyListingFilters(datos, "Mariana López", sinFiltro).map { it.id }
        assertTrue(conAcentos.isNotEmpty())
        assertEquals(conAcentos, applyListingFilters(datos, "MARIANA LOPEZ", sinFiltro).map { it.id })
        assertEquals(listOf("NH-8395"), applyListingFilters(datos, "economico", sinFiltro).map { it.id })
    }

    @Test fun busquedaYFiltrosSeCombinan() {
        val f = ListingFilters(statuses = emptySet(), modalities = setOf(ListingType.RENTA), types = setOf(PropertyType.DEPARTAMENTO))
        val res = applyListingFilters(datos, "", f)
        assertTrue(res.isNotEmpty())
        assertTrue(res.all { it.listingType == ListingType.RENTA && it.type == PropertyType.DEPARTAMENTO })
        val conTexto = applyListingFilters(datos, "mariana", f)
        assertTrue(conTexto.isNotEmpty() && conTexto.all { it.advertiser.name.contains("Mariana") })
    }

    @Test fun ordenAntiguosPrimero() {
        val res = applyListingFilters(datos, "", ListingFilters(statuses = emptySet(), order = SortOrder.OLDEST_FIRST))
        assertEquals(res.sortedBy { it.registeredAt }, res)
        assertEquals(datos.size, res.size)
    }

    @Test fun ordenNoCuentaComoFiltro() {
        assertEquals(1, ListingFilters().selectedCount)
        assertEquals(1, ListingFilters(order = SortOrder.OLDEST_FIRST).selectedCount)
        assertEquals(3, ListingFilters(
            statuses = setOf(ListingStatus.RECHAZADO), modalities = setOf(ListingType.VENTA), types = setOf(PropertyType.CASA)
        ).selectedCount)
    }

    @Test fun combinacionSinResultados() {
        val f = ListingFilters(statuses = setOf(ListingStatus.RECHAZADO), types = setOf(PropertyType.TERRENO))
        assertTrue(applyListingFilters(datos, "", f).isEmpty())
    }

    @Test fun botonesSegunEstado() {
        assertEquals(listOf(ListingAction.RECHAZAR, ListingAction.APROBAR, ListingAction.ELIMINAR), ListingAction.availableFor(ListingStatus.PENDIENTE))
        assertEquals(listOf(ListingAction.DESACTIVAR, ListingAction.ELIMINAR), ListingAction.availableFor(ListingStatus.ACTIVO))
        assertEquals(listOf(ListingAction.REACTIVAR, ListingAction.ELIMINAR), ListingAction.availableFor(ListingStatus.DESACTIVADO))
        assertEquals(listOf(ListingAction.APROBAR, ListingAction.ELIMINAR), ListingAction.availableFor(ListingStatus.RECHAZADO))
        assertTrue(ListingAction.availableFor(ListingStatus.RENTADO_VENDIDO).isEmpty())
    }
}
