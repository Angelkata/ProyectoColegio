package proyectoColegioReportes;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import proyectocolegio.bd.ConexionSQLite;

public class KardexService {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font CELL_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8);

    public void generarKardexPDF(int idAlumno, int idGrupo, String ciclo, String rutaSalidaPDF) throws Exception {
        try (Connection conn = ConexionSQLite.getConexion()) {
            AlumnoData alumno = cargarDatosAlumno(conn, idAlumno);
            GrupoData grupo = cargarDatosGrupo(conn, idGrupo);
            Map<Integer, MateriaData> materias = cargarMaterias(conn);
            Map<Integer, List<EvaluacionRegistro>> evaluaciones = cargarEvaluaciones(conn, idAlumno, idGrupo, ciclo);
            Map<Integer, MateriaCalculos> calculos = calcularPromedios(materias, evaluaciones);

            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, new FileOutputStream(rutaSalidaPDF));
            document.open();

            agregarEncabezado(document, alumno, grupo, ciclo);
            agregarSeccion(document, "Formación Académica", filtrarPorTipo(calculos, "ACADEMICA"));
            agregarSeccion(document, "Desarrollo Personal y Social", filtrarPorTipo(calculos, "DESARROLLO"));
            agregarSeccionAutonomia(document, "Autonomía Curricular", filtrarPorTipo(calculos, "AUTONOMIA"));
            agregarPieDePagina(document, calculos.values());

            document.close();
        } catch (DocumentException | IOException | SQLException ex) {
            throw new Exception("Error al generar kárdex: " + ex.getMessage(), ex);
        }
    }

    private AlumnoData cargarDatosAlumno(Connection conn, int idAlumno) throws SQLException {
        String sql = "SELECT nombre, segundo_nombre, primer_apellido, segundo_apellido, curp FROM alumnos WHERE id_alumno = ? AND eliminado = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idAlumno);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String nombre = rs.getString("nombre");
                    String segundoNombre = rs.getString("segundo_nombre");
                    String primerApellido = rs.getString("primer_apellido");
                    String segundoApellido = rs.getString("segundo_apellido");
                    String curp = rs.getString("curp");
                    StringBuilder nombreCompleto = new StringBuilder();
                    nombreCompleto.append(nombre);
                    if (segundoNombre != null && !segundoNombre.isBlank()) {
                        nombreCompleto.append(" ").append(segundoNombre);
                    }
                    nombreCompleto.append(" ").append(primerApellido);
                    if (segundoApellido != null && !segundoApellido.isBlank()) {
                        nombreCompleto.append(" ").append(segundoApellido);
                    }
                    return new AlumnoData(nombreCompleto.toString(), curp);
                }
            }
        }
        throw new SQLException("No se encontró el alumno con id " + idAlumno);
    }

    private GrupoData cargarDatosGrupo(Connection conn, int idGrupo) throws SQLException {
        String sql = "SELECT ciclo, nivel, grado, grupo, turno FROM grupos WHERE id_grupo = ? AND eliminado = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idGrupo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ciclo = rs.getString("ciclo");
                    String nivel = rs.getString("nivel");
                    int grado = rs.getInt("grado");
                    String grupo = rs.getString("grupo");
                    String turno = rs.getString("turno");
                    return new GrupoData(ciclo, nivel, grado, grupo, turno);
                }
            }
        }
        throw new SQLException("No se encontró el grupo con id " + idGrupo);
    }

    private Map<Integer, MateriaData> cargarMaterias(Connection conn) throws SQLException {
        String sql = "SELECT id_materia, nombre, tipo FROM materias WHERE eliminado = 0 ORDER BY nombre";
        Map<Integer, MateriaData> materias = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                MateriaData materia = new MateriaData(rs.getInt("id_materia"), rs.getString("nombre"), rs.getString("tipo"));
                materias.put(materia.id, materia);
            }
        }
        return materias;
    }

    private Map<Integer, List<EvaluacionRegistro>> cargarEvaluaciones(Connection conn, int idAlumno, int idGrupo, String ciclo) throws SQLException {
        String sql = "SELECT id_materia, trimestre, mes, calificacion, faltas FROM evaluaciones WHERE id_alumno = ? AND id_grupo = ? AND ciclo = ? AND eliminado = 0 AND tipo_nota = 'MENSUAL'";
        Map<Integer, List<EvaluacionRegistro>> datos = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idAlumno);
            ps.setInt(2, idGrupo);
            ps.setString(3, ciclo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EvaluacionRegistro reg = new EvaluacionRegistro(
                            rs.getInt("id_materia"),
                            rs.getInt("trimestre"),
                            rs.getInt("mes"),
                            rs.getObject("calificacion") != null ? rs.getDouble("calificacion") : null,
                            rs.getObject("faltas") != null ? rs.getInt("faltas") : null
                    );
                    datos.computeIfAbsent(reg.idMateria, k -> new ArrayList<>()).add(reg);
                }
            }
        }
        return datos;
    }

    private Map<Integer, MateriaCalculos> calcularPromedios(Map<Integer, MateriaData> materias, Map<Integer, List<EvaluacionRegistro>> evaluaciones) {
        Map<Integer, MateriaCalculos> resultado = new LinkedHashMap<>();
        for (MateriaData materia : materias.values()) {
            resultado.put(materia.id, new MateriaCalculos(materia));
        }

        for (Map.Entry<Integer, List<EvaluacionRegistro>> entry : evaluaciones.entrySet()) {
            MateriaCalculos calculo = resultado.get(entry.getKey());
            if (calculo == null) {
                continue;
            }
            for (EvaluacionRegistro reg : entry.getValue()) {
                if (reg.calificacion != null) {
                    calculo.calificacionesMensuales.put(reg.mes, reg.calificacion);
                    calculo.calificacionesTrimestre.computeIfAbsent(reg.trimestre, k -> new ArrayList<>()).add(reg.calificacion);
                }
                if (reg.faltas != null) {
                    calculo.faltasTotales += reg.faltas;
                    calculo.faltasTrimestre.merge(reg.trimestre, reg.faltas, Integer::sum);
                }
            }
        }

        for (MateriaCalculos calculo : resultado.values()) {
            for (int trimestre : Arrays.asList(1, 2, 3)) {
                List<Double> lista = calculo.calificacionesTrimestre.getOrDefault(trimestre, Collections.emptyList());
                calculo.promedioTrimestre.put(trimestre, lista.isEmpty() ? null : lista.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN));
            }
            List<Double> proms = calculo.promedioTrimestre.values().stream().filter(Objects::nonNull).collect(Collectors.toList());
            calculo.promedioFinal = proms.isEmpty() ? null : proms.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        }
        return resultado;
    }

    private void agregarEncabezado(Document document, AlumnoData alumno, GrupoData grupo, String ciclo) throws DocumentException {
        Paragraph titulo = new Paragraph("KÁRDEX DEL ALUMNO", TITLE_FONT);
        titulo.setAlignment(Element.ALIGN_CENTER);
        document.add(titulo);
        document.add(new Paragraph(""));

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell(new Phrase("LOGO", CELL_HEADER_FONT));
        logoCell.setFixedHeight(50);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        info.addCell(logoCell);

        PdfPTable datos = new PdfPTable(2);
        datos.setWidthPercentage(100);
        datos.addCell(celdaEtiquetaValor("Alumno:", alumno.nombreCompleto));
        datos.addCell(celdaEtiquetaValor("CURP:", alumno.curp));
        datos.addCell(celdaEtiquetaValor("Grado:", String.valueOf(grupo.grado)));
        datos.addCell(celdaEtiquetaValor("Grupo:", grupo.grupo != null ? grupo.grupo : "Único"));
        datos.addCell(celdaEtiquetaValor("Nivel:", grupo.nivel != null ? grupo.nivel : ""));
        datos.addCell(celdaEtiquetaValor("Turno:", grupo.turno != null ? grupo.turno : ""));
        datos.addCell(celdaEtiquetaValor("Ciclo escolar:", ciclo != null ? ciclo : grupo.ciclo));
        PdfPCell datosCell = new PdfPCell(datos);
        datosCell.setBorder(PdfPCell.NO_BORDER);
        info.addCell(datosCell);

        document.add(info);
        document.add(new Paragraph(""));
    }

    private void agregarSeccion(Document document, String titulo, List<MateriaCalculos> materias) throws DocumentException {
        if (materias.isEmpty()) {
            return;
        }
        document.add(new Paragraph(titulo, SECTION_FONT));
        document.add(new Paragraph(""));

        String[] encabezados = new String[]{
            "Materia", "Sep", "Oct", "Nov", "Prom 1er Trim",
            "Dic", "Ene", "Feb", "Prom 2do Trim",
            "Mar/Abr", "May", "Jun", "Prom 3er Trim",
            "Prom Final", "Faltas"
        };
        PdfPTable tabla = new PdfPTable(encabezados.length);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String enc : encabezados) {
            PdfPCell cell = new PdfPCell(new Phrase(enc, CELL_HEADER_FONT));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(cell);
        }

        for (MateriaCalculos materia : materias) {
            tabla.addCell(new Phrase(materia.materia.nombre, CELL_FONT));
            tabla.addCell(valorCelda(materia.getNotaMes(9)));
            tabla.addCell(valorCelda(materia.getNotaMes(10)));
            tabla.addCell(valorCelda(materia.getNotaMes(11)));
            tabla.addCell(valorCelda(materia.promedioTrimestre.get(1)));
            tabla.addCell(valorCelda(materia.getNotaMes(12)));
            tabla.addCell(valorCelda(materia.getNotaMes(1)));
            tabla.addCell(valorCelda(materia.getNotaMes(2)));
            tabla.addCell(valorCelda(materia.promedioTrimestre.get(2)));
            tabla.addCell(valorCelda(materia.getPromedioMarAbr()));
            tabla.addCell(valorCelda(materia.getNotaMes(5)));
            tabla.addCell(valorCelda(materia.getNotaMes(6)));
            tabla.addCell(valorCelda(materia.promedioTrimestre.get(3)));
            tabla.addCell(valorCelda(materia.promedioFinal));
            tabla.addCell(valorCelda(materia.faltasTotales));
        }

        document.add(tabla);
        document.add(new Paragraph(""));
    }

    private void agregarSeccionAutonomia(Document document, String titulo, List<MateriaCalculos> materias) throws DocumentException {
        if (materias.isEmpty()) {
            return;
        }
        document.add(new Paragraph(titulo, SECTION_FONT));
        document.add(new Paragraph(""));

        String[] encabezados = new String[]{"Materia", "Prom 1er Trim", "Prom 2do Trim", "Prom 3er Trim", "Prom Final", "Faltas"};
        PdfPTable tabla = new PdfPTable(encabezados.length);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String enc : encabezados) {
            PdfPCell cell = new PdfPCell(new Phrase(enc, CELL_HEADER_FONT));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(cell);
        }
        for (MateriaCalculos materia : materias) {
            tabla.addCell(new Phrase(materia.materia.nombre, CELL_FONT));
            tabla.addCell(valorCelda(materia.promedioTrimestre.get(1)));
            tabla.addCell(valorCelda(materia.promedioTrimestre.get(2)));
            tabla.addCell(valorCelda(materia.promedioTrimestre.get(3)));
            tabla.addCell(valorCelda(materia.promedioFinal));
            tabla.addCell(valorCelda(materia.faltasTotales));
        }
        document.add(tabla);
        document.add(new Paragraph(""));
    }

    private void agregarPieDePagina(Document document, Iterable<MateriaCalculos> materias) throws DocumentException {
        double promedioGeneral = obtenerPromedioGeneral(materias);
        int faltasTotales = obtenerFaltasTotales(materias);

        Paragraph resumen = new Paragraph("Promedio general: " + (Double.isNaN(promedioGeneral) ? "" : formatear(promedioGeneral))
                + "    Total de faltas: " + faltasTotales, SECTION_FONT);
        document.add(resumen);
        document.add(new Paragraph(""));

        Paragraph observaciones = new Paragraph("Observaciones:\n\n\n\n________________________________________\nFirma del padre o tutor", CELL_FONT);
        document.add(observaciones);
        document.add(new Paragraph("\n\nSello de la escuela", CELL_FONT));
    }

    private List<MateriaCalculos> filtrarPorTipo(Map<Integer, MateriaCalculos> calculos, String tipo) {
        return calculos.values().stream()
                .filter(m -> tipo.equalsIgnoreCase(m.materia.tipo))
                .sorted(Comparator.comparing(m -> m.materia.nombre))
                .collect(Collectors.toList());
    }

    private PdfPCell celdaEtiquetaValor(String etiqueta, String valor) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(new Phrase(etiqueta, CELL_HEADER_FONT));
        table.addCell(new Phrase(valor != null ? valor : "", CELL_FONT));
        PdfPCell cell = new PdfPCell(table);
        cell.setBorder(PdfPCell.NO_BORDER);
        return cell;
    }

    private Phrase valorCelda(Double valor) {
        return new Phrase(valor == null || Double.isNaN(valor) ? "" : formatear(valor), CELL_FONT);
    }

    private Phrase valorCelda(Integer valor) {
        return new Phrase(valor == null ? "" : valor.toString(), CELL_FONT);
    }

    private String formatear(double valor) {
        return String.format("%.2f", valor);
    }

    private double obtenerPromedioGeneral(Iterable<MateriaCalculos> materias) {
        List<Double> finales = new ArrayList<>();
        for (MateriaCalculos m : materias) {
            if (m.promedioFinal != null && !Double.isNaN(m.promedioFinal)) {
                finales.add(m.promedioFinal);
            }
        }
        OptionalDouble avg = finales.stream().mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? avg.getAsDouble() : Double.NaN;
    }

    private int obtenerFaltasTotales(Iterable<MateriaCalculos> materias) {
        int total = 0;
        for (MateriaCalculos m : materias) {
            total += m.faltasTotales;
        }
        return total;
    }

    private static class AlumnoData {

        final String nombreCompleto;
        final String curp;

        AlumnoData(String nombreCompleto, String curp) {
            this.nombreCompleto = nombreCompleto;
            this.curp = curp;
        }
    }

    private static class GrupoData {

        final String ciclo;
        final String nivel;
        final int grado;
        final String grupo;
        final String turno;

        GrupoData(String ciclo, String nivel, int grado, String grupo, String turno) {
            this.ciclo = ciclo;
            this.nivel = nivel;
            this.grado = grado;
            this.grupo = grupo;
            this.turno = turno;
        }
    }

    private static class MateriaData {

        final int id;
        final String nombre;
        final String tipo;

        MateriaData(int id, String nombre, String tipo) {
            this.id = id;
            this.nombre = nombre;
            this.tipo = tipo;
        }
    }

    private static class EvaluacionRegistro {

        final int idMateria;
        final int trimestre;
        final int mes;
        final Double calificacion;
        final Integer faltas;

        EvaluacionRegistro(int idMateria, int trimestre, int mes, Double calificacion, Integer faltas) {
            this.idMateria = idMateria;
            this.trimestre = trimestre;
            this.mes = mes;
            this.calificacion = calificacion;
            this.faltas = faltas;
        }
    }

    private static class MateriaCalculos {

        final MateriaData materia;
        final Map<Integer, List<Double>> calificacionesTrimestre = new HashMap<>();
        final Map<Integer, Integer> faltasTrimestre = new HashMap<>();
        final Map<Integer, Double> calificacionesMensuales = new HashMap<>();
        final Map<Integer, Double> promedioTrimestre = new HashMap<>();
        Double promedioFinal;
        int faltasTotales = 0;

        MateriaCalculos(MateriaData materia) {
            this.materia = materia;
        }

        Double getNotaMes(int mes) {
            return calificacionesMensuales.get(mes);
        }

        Double getPromedioMarAbr() {
            Double marzo = calificacionesMensuales.get(3);
            Double abril = calificacionesMensuales.get(4);
            if (marzo == null && abril == null) {
                return null;
            }
            List<Double> valores = new ArrayList<>();
            if (marzo != null) {
                valores.add(marzo);
            }
            if (abril != null) {
                valores.add(abril);
            }
            return valores.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        }
    }
}
