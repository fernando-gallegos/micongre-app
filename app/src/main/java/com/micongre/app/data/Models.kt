package com.micongre.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Mismos contratos que el backend web (ver docs/mobile-activation-protocol.md
// y src/lib/queries/panel.ts en micongre-admin) — nada de esto se inventa del
// lado de la app, solo se refleja.

@Serializable
data class LoginRequest(val username: String, val password: String, val clientType: String = "mobile")

/** «¿Olvidaste tu contraseña?»: usuario o nombre completo (ver POST /api/auth/forgot en la web). */
@Serializable
data class ForgotPasswordRequest(val identifier: String)

@Serializable
data class ActivatePreviewResponse(
	val fullName: String,
	val suggestedUsername: String,
	val suggestedPassword: String,
	val usernameEditable: Boolean,
)

@Serializable
data class ActivateRequest(val token: String, val username: String, val password: String)

@Serializable
data class AuthResponse(
	val username: String? = null,
	val password: String? = null,
	val accessToken: String,
	val refreshToken: String,
	val expiresIn: Int,
	val user: SessionUser,
)

@Serializable
data class SessionUser(val id: String, val fullName: String, val role: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(val accessToken: String, val refreshToken: String? = null, val expiresIn: Int)

/** GET /api/auth/me: identidad, privilegios y nombramientos vigentes (de ahí sale todo el acceso por módulos). */
@Serializable
data class Me(
	val id: String,
	val fullName: String,
	val username: String? = null,
	val systemRole: String = "regular",
	val fieldServiceGroupId: String? = null,
	val navShortcut: String? = null,
	val privilegeCodes: List<String> = emptyList(),
	val appointmentTypes: List<String> = emptyList(),
) {
	val isAdmin get() = systemRole == "admin"
	fun has(vararg codes: String) = isAdmin || codes.any { it in privilegeCodes }
	fun hasAppointment(vararg types: String) = types.any { it in appointmentTypes }
}

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class ShortcutRequest(val href: String?)

// --- Mi Panel ---

@Serializable
data class TerritoryRef(val assignmentId: String, val territoryId: String, val status: String, val comment: String? = null)

@Serializable
data class PanelAssignment(
	val date: String,
	val dateEnd: String? = null,
	val meetingType: String,
	val label: String,
	val territory: TerritoryRef? = null,
)

@Serializable
data class GroupMember(val id: String, val fullName: String, val phone: String? = null, val activeAppointmentTypes: List<String> = emptyList())

@Serializable
data class PanelGroup(
	val id: String,
	val name: String,
	val color: String? = null,
	val overseerName: String? = null,
	val assistantName: String? = null,
	val members: List<GroupMember> = emptyList(),
)

@Serializable
data class PanelLink(val kind: String, val label: String, val url: String, val color: String)

@Serializable
data class PanelSummary(
	val upcoming: List<PanelAssignment> = emptyList(),
	val history: List<PanelAssignment> = emptyList(),
	val group: PanelGroup? = null,
	val links: List<PanelLink> = emptyList(),
)

@Serializable
data class NotificationItem(
	val id: String,
	val scope: String,
	val message: String,
	val createdAt: String,
	val senderName: String,
)

@Serializable
data class NotificationsResponse(val items: List<NotificationItem> = emptyList())

/** Aviso enviado (historial de quien notifica): un admin ve todos, los demás solo los suyos. */
@Serializable
data class SentNotification(
	val id: String,
	val scope: String,
	val groupName: String? = null,
	val message: String,
	val expiresAt: String,
	val createdAt: String,
	val senderName: String,
	val recipientNames: List<String> = emptyList(),
)

@Serializable
data class SentNotificationsResponse(val items: List<SentNotification> = emptyList())

@Serializable
data class CreateNotificationRequest(
	val scope: String,
	val message: String,
	val expiresAt: String,
	val recipientUserIds: List<String>? = null,
)

// --- Pastoreo ---

@Serializable
data class VisitRequest(
	val id: String,
	val requestedDate: String,
	val notes: String? = null,
	val assignedElders: List<String>? = null,
	val status: String,
)

@Serializable
data class VisitRequestsResponse(val items: List<VisitRequest> = emptyList())

@Serializable
data class CreateVisitRequest(val requestedDate: String, val notes: String? = null)

@Serializable
data class CreatedResponse(val id: String)

@Serializable
data class AssignedVisit(
	val id: String,
	val publisherName: String,
	val requestedDate: String,
	val notes: String? = null,
	val status: String = "pending",
	val coElderName: String? = null,
)

@Serializable
data class AssignedVisitsResponse(val items: List<AssignedVisit> = emptyList())

@Serializable
data class VisitStatusRequest(val status: String)

// --- Territorios (solo las asignaciones propias) ---

@Serializable
data class ResolveTerritoryRequest(val status: String, val comment: String? = null)

@Serializable
data class TerritoryMapResponse(
	val number: String,
	val kind: String = "general",
	val name: String? = null,
	val notes: String? = null,
	val mapData: JsonElement? = null,
)

// --- Arreglos de discursos ---

@Serializable
data class Arrangement(
	val id: String,
	val congregationId: String,
	val direction: String,
	val year: Int,
	val month: Int,
	val status: String,
	val proposedYear: Int? = null,
	val proposedMonth: Int? = null,
	val requestId: String? = null,
)

@Serializable
data class ArrangementsResponse(val items: List<Arrangement> = emptyList())

@Serializable
data class SpeakerCongregation(val id: String, val name: String)

@Serializable
data class SpeakerCongregationsResponse(val items: List<SpeakerCongregation> = emptyList())

@Serializable
data class ArrangementActionRequest(val action: String, val year: Int? = null, val month: Int? = null, val comment: String? = null)

// --- Reuniones ---

@Serializable
data class MidweekListItem(
	val id: String,
	val meetingDate: String,
	val weekLabel: String? = null,
	val status: String = "scheduled",
)

@Serializable
data class MidweekListResponse(val items: List<MidweekListItem> = emptyList())

@Serializable
data class MidweekMeeting(
	val id: String,
	val meetingDate: String,
	val weekLabel: String? = null,
	val status: String = "scheduled",
	val chairmanName: String? = null,
	val openingPrayerName: String? = null,
	val openingSongNumber: Int? = null,
	val openingSongTitle: String? = null,
	val middleSongNumber: Int? = null,
	val middleSongTitle: String? = null,
	val closingSongNumber: Int? = null,
	val closingSongTitle: String? = null,
	val hasAuxRoom: Boolean = false,
	val notes: String? = null,
)

@Serializable
data class MidweekAssignment(
	val id: String,
	val sectionType: String,
	val sectionTitle: String? = null,
	val description: String? = null,
	val sequenceOrder: Int = 0,
	val partNumber: Int? = null,
	val room: String = "main",
	val durationMinutes: Int? = null,
	val mainAssigneeName: String? = null,
	val assistantAssigneeName: String? = null,
)

@Serializable
data class MidweekDetail(
	val meeting: MidweekMeeting,
	val assignments: List<MidweekAssignment> = emptyList(),
	val skipOpeningPrayer: Boolean = false,
)

@Serializable
data class WeekendListItem(val id: String, val meetingDate: String, val status: String = "scheduled")

@Serializable
data class WeekendListResponse(val items: List<WeekendListItem> = emptyList())

@Serializable
data class WeekendMeeting(
	val id: String,
	val meetingDate: String,
	val status: String = "scheduled",
	val notes: String? = null,
	val chairmanName: String? = null,
	val watchtowerConductorName: String? = null,
	val watchtowerReaderName: String? = null,
	val prayerName: String? = null,
	val speakerName: String? = null,
	val speakerCongregationName: String? = null,
	val talkOutlineNumber: Int? = null,
	val talkOutlineTitle: String? = null,
	/** Canción del discurso: la que el orador tiene asignada a ese bosquejo. */
	val talkSongNumber: Int? = null,
	val talkSongTitle: String? = null,
	/** La reunión empieza con La Atalaya y el discurso público va después. */
	val watchtowerFirst: Boolean = false,
	val watchtowerTitle: String? = null,
	val watchtowerSongNumber: Int? = null,
	val watchtowerSongTitle: String? = null,
	val closingSongNumber: Int? = null,
	val closingSongTitle: String? = null,
)

@Serializable
data class WeekendDetail(val meeting: WeekendMeeting)

@Serializable
data class OperationalItem(
	val id: String,
	val privilegeId: String,
	val privilegeName: String,
	val position: String? = null,
	val assignedUserName: String? = null,
)

@Serializable
data class OperationalViewResponse(val items: List<OperationalItem> = emptyList())

// --- Informes (S-21) ---

@Serializable
data class S21Publisher(
	val id: String,
	val fullName: String,
	val birthDate: String? = null,
	val baptismDate: String? = null,
	val gender: String? = null,
)

@Serializable
data class S21Appointment(val appointmentType: String, val startDate: String? = null, val endDate: String? = null)

@Serializable
data class ServiceReport(
	val periodYear: Int,
	val periodMonth: Int,
	val participatedInMinistry: Boolean = false,
	val isAuxiliaryPioneerThisMonth: Boolean = false,
	val hours: Double? = null,
	val projectHours: Double? = null,
	val bibleStudiesCount: Int? = null,
	val creditHoursNote: String? = null,
	val comments: String? = null,
	/** Cuándo se envió (ISO): es la versión del informe para detectar choques al enviar lo guardado sin internet. */
	val submittedAt: String? = null,
)

@Serializable
data class S21Response(
	val publisher: S21Publisher,
	val appointments: List<S21Appointment> = emptyList(),
	val reports: List<ServiceReport> = emptyList(),
	val canEdit: Boolean = false,
)

@Serializable
data class S21SaveRequest(
	val periodYear: Int,
	val periodMonth: Int,
	val participatedInMinistry: Boolean,
	val isAuxiliaryPioneerThisMonth: Boolean,
	val hours: Double? = null,
	val projectHours: Double? = null,
	val bibleStudiesCount: Int = 0,
	val comments: String? = null,
)

// --- Publicadores ---

@Serializable
data class PublisherAppointment(val appointmentType: String, val endDate: String? = null)

@Serializable
data class PublisherItem(
	val id: String,
	val fullName: String,
	val gender: String? = null,
	val phone: String? = null,
	val email: String? = null,
	val birthDate: String? = null,
	val baptismDate: String? = null,
	val emergencyContactName: String? = null,
	val emergencyContactPhone: String? = null,
	val emergencyContactRelation: String? = null,
	val hasDpaCard: Boolean = false,
	val isEligibleForReducedReport: Boolean = false,
	val congregationStatus: String = "active",
	val theocraticStatus: String? = null,
	val accountStatus: String = "no_account",
	val fieldServiceGroupId: String? = null,
	val systemRole: String = "regular",
	val isHiddenAdmin: Boolean = false,
	val disfellowshipped: Boolean = false,
	val appointments: List<PublisherAppointment> = emptyList(),
	val serviceYearHours: Double? = null,
)

@Serializable
data class RegisterDeviceRequest(val fcmToken: String, val publicKey: String? = null)

@Serializable
data class DeviceConfig(val fcm: Boolean = false)

/** GET /api/reports/s4/mine: el informe del mes (si existe) y el tipo de precursor de la persona. */
@Serializable
data class MyReportResponse(val report: ServiceReport? = null, val pioneerType: String? = null)

@Serializable
data class S4SaveRequest(
	val userId: String,
	val periodYear: Int,
	val periodMonth: Int,
	val participatedInMinistry: Boolean,
	val isAuxiliaryPioneerThisMonth: Boolean = false,
	val hours: Double? = null,
	val projectHours: Double? = null,
	val bibleStudiesCount: Int = 0,
	val comments: String? = null,
)

/** Horas de un mes del año de servicio (servicio + proyectos). */
@Serializable
data class ServiceYearMonth(val year: Int, val month: Int, val hours: Double = 0.0)

/** Horas del año de servicio en curso (septiembre a agosto) de quien usa la app; `months` va en ese orden. */
@Serializable
data class ServiceYearResponse(
	val isRegularPioneer: Boolean = false,
	val serviceYear: Int = 0,
	val hours: Double = 0.0,
	val months: List<ServiceYearMonth> = emptyList(),
)

// --- Predicación (calendario de solo lectura) ---

@Serializable
data class PreachingTerritory(val number: String, val name: String? = null, val assigneeName: String? = null)

@Serializable
data class PreachingGroup(val name: String)

@Serializable
data class PreachingPerson(val fullName: String)

@Serializable
data class PreachingTargets(
	val congregation: Boolean = false,
	val groups: List<PreachingGroup> = emptyList(),
	val users: List<PreachingPerson> = emptyList(),
)

@Serializable
data class PreachingSession(
	val id: String,
	val date: String,
	val time: String,
	val note: String? = null,
	/** "general" o "public_preaching". */
	val kind: String = "general",
	val territories: List<PreachingTerritory> = emptyList(),
	val targets: PreachingTargets = PreachingTargets(),
)

@Serializable
data class PreachingMonthResponse(val items: List<PreachingSession> = emptyList(), val canManage: Boolean = false)

@Serializable
data class PreachingOptTerritory(val id: String, val number: String, val name: String? = null, val kind: String = "general", val assigneeName: String? = null)

@Serializable
data class PreachingOptGroup(val id: String, val name: String)

@Serializable
data class PreachingOptPerson(val id: String, val fullName: String)

@Serializable
data class PreachingOptions(
	val territories: List<PreachingOptTerritory> = emptyList(),
	val groups: List<PreachingOptGroup> = emptyList(),
	val people: List<PreachingOptPerson> = emptyList(),
	val defaultTime: String = "09:00",
)

@Serializable
data class PreachingTargetsBody(val congregation: Boolean, val groupIds: List<String>, val userIds: List<String>)

@Serializable
data class CreatePreachingRequest(val date: String, val time: String, val note: String? = null, val territoryIds: List<String>, val targets: PreachingTargetsBody)

// --- Territorios (Predicación → Territorios): lo administra quien tiene "Siervo de territorios" ---

/** Asignación de un territorio; `status` es "assigned", "completed" o "returned". Las fechas llegan como texto ISO. */
@Serializable
data class TerritoryAssignment(
	val id: String,
	val assigneeUserId: String,
	val assigneeName: String,
	val assignedAt: String? = null,
	val status: String = "assigned",
	val resolvedAt: String? = null,
	val resolutionComment: String? = null,
	val notifiedAt: String? = null,
)

@Serializable
data class TerritoryItem(
	val id: String,
	val number: String,
	/** "general" o "public_preaching". */
	val kind: String = "general",
	val name: String? = null,
	val notes: String? = null,
	val hasMap: Boolean = false,
	val current: TerritoryAssignment? = null,
	val last: TerritoryAssignment? = null,
)

@Serializable
data class TerritoryListResponse(val items: List<TerritoryItem> = emptyList())

/** Una fila del historial/lista de asignaciones (GET /api/territories/assignments). */
@Serializable
data class TerritoryAssignmentRow(
	val id: String,
	val territoryId: String,
	val territoryNumber: String,
	val territoryName: String? = null,
	val territoryKind: String = "general",
	val assigneeUserId: String,
	val assigneeName: String,
	val assignedAt: String? = null,
	val status: String = "assigned",
	val resolvedAt: String? = null,
	val resolutionComment: String? = null,
	val notifiedAt: String? = null,
)

@Serializable
data class TerritoryAssignmentListResponse(val items: List<TerritoryAssignmentRow> = emptyList())

@Serializable
data class TerritoryAssignee(val id: String, val fullName: String)

@Serializable
data class TerritoryAssigneesResponse(val items: List<TerritoryAssignee> = emptyList())

@Serializable
data class SaveTerritoryRequest(val number: String, val kind: String, val name: String, val notes: String)

@Serializable
data class AssignTerritoryRequest(val assigneeUserId: String)

// --- Asistencia a las reuniones (Informes → Asistencia) ---

/** Cifras ya registradas de una reunión; `meetingType` es "midweek" o "weekend". */
@Serializable
data class AttendanceItem(
	val meetingType: String,
	val meetingId: String,
	val inPersonCount: Int = 0,
	val zoomCount: Int = 0,
	/** Cuándo se registró (ISO): es la versión del registro para detectar choques al enviar lo guardado sin internet. */
	val recordedAt: String? = null,
)

@Serializable
data class AttendanceOneResponse(val item: AttendanceItem? = null)

@Serializable
data class AttendanceListResponse(val items: List<AttendanceItem> = emptyList())

/** Promedio de un tipo de reunión en un mes (presencial + Zoom por reunión). */
@Serializable
data class AttendanceMonthStat(
	val meetings: Int = 0,
	val average: Double = 0.0,
	val averageInPerson: Double = 0.0,
	val averageZoom: Double = 0.0,
)

@Serializable
data class AttendanceMonth(val month: String, val midweek: AttendanceMonthStat? = null, val weekend: AttendanceMonthStat? = null)

@Serializable
data class AttendanceSummaryResponse(val months: List<AttendanceMonth> = emptyList())

@Serializable
data class AttendanceRequest(val meetingType: String, val meetingId: String, val meetingDate: String, val inPersonCount: Int, val zoomCount: Int)

@Serializable
data class S4Person(val userId: String, val fullName: String)

@Serializable
data class S4ListResponse(val items: List<S4Person> = emptyList())

@Serializable
data class PublishersResponse(
	val items: List<PublisherItem> = emptyList(),
	val nextCursor: Int? = null,
	val canViewProgress: Boolean = false,
)

/** Informes de un mes agrupados por grupo de servicio (pestaña «Grupos» de Informes). */
@Serializable
data class GroupReportItem(
	val userId: String,
	val fullName: String,
	val reportId: String? = null,
	val participatedInMinistry: Boolean? = null,
	val isAuxiliaryPioneerThisMonth: Boolean? = null,
	val hours: Double? = null,
	val projectHours: Double? = null,
	val bibleStudiesCount: Int? = null,
	val comments: String? = null,
	val pioneerType: String? = null,
)

@Serializable
data class GroupReports(val id: String? = null, val name: String, val items: List<GroupReportItem> = emptyList())

@Serializable
data class GroupReportsResponse(val groups: List<GroupReports> = emptyList())
