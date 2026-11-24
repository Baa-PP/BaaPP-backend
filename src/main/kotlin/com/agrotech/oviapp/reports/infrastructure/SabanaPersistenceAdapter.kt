package com.agrotech.oviapp.reports.infrastructure

import com.agrotech.oviapp.reports.domain.SabanaPage
import com.agrotech.oviapp.reports.domain.SabanaPolicy
import com.agrotech.oviapp.reports.domain.SabanaQuery
import com.agrotech.oviapp.reports.domain.SabanaReadPort
import com.agrotech.oviapp.reports.domain.SabanaRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate

@Component
class SabanaPersistenceAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SabanaReadPort {

    override fun findPage(query: SabanaQuery): SabanaPage {
        val filter = buildWhereClause(query)

        val countSql = "SELECT COUNT(*) FROM v_sabana ${filter.sql}"
        val totalElements = jdbcTemplate.query(
            PreparedStatementCreator { conn ->
                conn.prepareStatement(countSql).also { ps -> bindParams(ps, filter.params) }
            },
            ResultSetExtractor { rs -> if (rs.next()) rs.getLong(1) else 0L },
        ) ?: 0L

        if (totalElements == 0L) {
            return SabanaPage(
                items = emptyList(),
                page = query.page,
                size = query.size,
                totalElements = 0L,
                totalPages = 0,
            )
        }

        val offset = query.page.toLong() * query.size.toLong()
        val pageSql = "$BASE_SELECT ${filter.sql} $ORDER_BY LIMIT ? OFFSET ?"
        val pageParams = filter.params + listOf(query.size, offset)

        val items = jdbcTemplate.query(
            PreparedStatementCreator { conn ->
                conn.prepareStatement(pageSql).also { ps -> bindParams(ps, pageParams) }
            },
            RowMapper { rs, _ -> mapRow(rs) },
        )

        val totalPages = ((totalElements + query.size - 1L) / query.size).toInt()
        return SabanaPage(
            items = items,
            page = query.page,
            size = query.size,
            totalElements = totalElements,
            totalPages = totalPages,
        )
    }

    override fun streamAll(query: SabanaQuery, consumer: (SabanaRow) -> Unit) {
        val filter = buildWhereClause(query)
        val sql = "$BASE_SELECT ${filter.sql} $ORDER_BY"

        jdbcTemplate.query(
            PreparedStatementCreator { conn ->
                conn.prepareStatement(
                    sql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY,
                ).apply {
                    fetchSize = STREAM_FETCH_SIZE
                    bindParams(this, filter.params)
                }
            },
            RowCallbackHandler { rs ->
                consumer(mapRow(rs))
            },
        )
    }

    private data class WhereClause(
        val sql: String,
        val params: List<Any>,
    )

    private fun buildWhereClause(query: SabanaQuery): WhereClause {
        val conditions = mutableListOf("rancho_id = ?")
        val params = mutableListOf<Any>(query.ranchId)

        SabanaPolicy.toContainsLikePattern(query.search)?.let { pattern ->
            conditions += "(folio ILIKE ? ESCAPE '!' OR chip ILIKE ? ESCAPE '!' OR adn ILIKE ? ESCAPE '!')"
            params += pattern
            params += pattern
            params += pattern
        }

        SabanaPolicy.toSabanaSexFilter(query.sex)?.let { sabanaSex ->
            conditions += "sexo = ?"
            params += sabanaSex
        }

        SabanaPolicy.toContainsLikePattern(query.breed)?.let { breedPattern ->
            conditions += "raza ILIKE ? ESCAPE '!'"
            params += breedPattern
        }

        query.status?.let { status ->
            conditions += "estado = ?"
            params += status.name
        }

        return WhereClause(
            sql = "WHERE ${conditions.joinToString(" AND ")}",
            params = params,
        )
    }

    private fun bindParams(ps: PreparedStatement, params: List<Any>) {
        params.forEachIndexed { index, value ->
            ps.setObject(index + 1, value)
        }
    }

    private fun mapRow(rs: ResultSet): SabanaRow {
        val sexo = rs.getString("sexo")
        val rawCircunferencia = rs.getBigDecimal("circunferencia")
        return SabanaRow(
            folio = rs.getString("folio"),
            adn = rs.getString("adn"),
            chipSiniiga = rs.getString("chip"),
            fechaNacimiento = rs.getObject("fecha_nacimiento", LocalDate::class.java),
            fechaDestete = rs.getObject("fecha_destete", LocalDate::class.java),
            edadMeses = rs.getBigDecimal("edad_meses") ?: BigDecimal.ZERO,
            edadDias = rs.getInt("edad_dias"),
            pesoNacimientoKg = rs.getBigDecimal("peso_nacimiento"),
            peso120DiasKg = rs.getBigDecimal("peso_120_dias"),
            peso150DiasKg = rs.getBigDecimal("peso_150_dias"),
            pesoEvaluacionKg = rs.getBigDecimal("peso_evaluacion"),
            circunferenciaEscrotalCm = if (sexo.equals("HEMBRA", ignoreCase = true)) null else rawCircunferencia,
            pesoDesteteKg = rs.getBigDecimal("peso_destete"),
            tipoDestete = rs.getString("tipo_destete"),
            padreFolioInterno = rs.getString("padre"),
            folioPadreUno = rs.getString("folio_padre_uno"),
            madreFolioInterno = rs.getString("madre"),
            folioMadreUno = rs.getString("folio_madre_uno"),
            raza = rs.getString("raza"),
            tipoParto = rs.getString("tipo_parto"),
            concepcion = rs.getString("concepcion"),
            color = rs.getString("color"),
            sexo = sexo,
            fechaCorteIso = rs.getObject("fecha_corte", LocalDate::class.java),
        )
    }

    companion object {
        private const val STREAM_FETCH_SIZE = 200

        private const val BASE_SELECT = """
            SELECT folio,
                   adn,
                   chip,
                   fecha_nacimiento,
                   fecha_destete,
                   edad_meses,
                   edad_dias,
                   peso_nacimiento,
                   peso_120_dias,
                   peso_150_dias,
                   peso_evaluacion,
                   circunferencia,
                   peso_destete,
                   tipo_destete,
                   padre,
                   folio_padre_uno,
                   madre,
                   folio_madre_uno,
                   raza,
                   tipo_parto,
                   concepcion,
                   color,
                   sexo,
                   fecha_corte
            FROM v_sabana
        """

        private const val ORDER_BY = "ORDER BY folio ASC, animal_id ASC"
    }
}
