package com.micongre.app.data

import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Un endpoint por línea, todos ya existentes en micongre-admin — ver docs/mobile-activation-protocol.md ahí
 * para el contrato exacto de activación/login/refresh. */
interface ApiService {
	@GET("api/auth/activate")
	suspend fun previewActivation(@Query("token") token: String): Response<ActivatePreviewResponse>

	@POST("api/auth/activate")
	suspend fun activate(@Body body: ActivateRequest): Response<AuthResponse>

	@POST("api/auth/login")
	suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

	@POST("api/auth/refresh")
	suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>

	@GET("api/auth/me")
	suspend fun me(): Response<Me>

	@POST("api/auth/delete-account")
	suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<OkResponse>

	@PUT("api/auth/shortcut")
	suspend fun setShortcut(@Body body: ShortcutRequest): Response<OkResponse>

	// Mi Panel
	@GET("api/panel/summary")
	suspend fun panelSummary(): Response<PanelSummary>

	@GET("api/notifications/mine")
	suspend fun notifications(): Response<NotificationsResponse>

	@GET("api/notifications")
	suspend fun sentNotifications(): Response<SentNotificationsResponse>

	@POST("api/notifications")
	suspend fun sendNotification(@Body body: CreateNotificationRequest): Response<CreatedResponse>

	@POST("api/notifications/{id}/dismiss")
	suspend fun dismissNotification(@Path("id") id: String): Response<OkResponse>

	@GET("api/pastoral/visit-requests/mine")
	suspend fun myVisitRequests(): Response<VisitRequestsResponse>

	@POST("api/pastoral/visit-requests/mine")
	suspend fun createVisitRequest(@Body body: CreateVisitRequest): Response<CreatedResponse>

	@DELETE("api/pastoral/visit-requests/{id}")
	suspend fun cancelVisitRequest(@Path("id") id: String): Response<OkResponse>

	@GET("api/pastoral/visit-requests/assigned-to-me")
	suspend fun assignedVisits(): Response<AssignedVisitsResponse>

	@PATCH("api/pastoral/visit-requests/{id}/status")
	suspend fun setVisitStatus(@Path("id") id: String, @Body body: VisitStatusRequest): Response<OkResponse>

	@GET("api/territories/{id}/map")
	suspend fun territoryMap(@Path("id") id: String): Response<TerritoryMapResponse>

	@POST("api/territories/assignments/{id}/resolve")
	suspend fun resolveTerritory(@Path("id") id: String, @Body body: ResolveTerritoryRequest): Response<OkResponse>

	@GET("api/weekend/arrangements")
	suspend fun arrangements(): Response<ArrangementsResponse>

	@GET("api/weekend/congregations")
	suspend fun speakerCongregations(): Response<SpeakerCongregationsResponse>

	@POST("api/weekend/arrangements/{id}/action")
	suspend fun arrangementAction(@Path("id") id: String, @Body body: ArrangementActionRequest): Response<JsonElement>

	@DELETE("api/weekend/arrangements/{id}")
	suspend fun deleteArrangement(@Path("id") id: String, @Query("force") force: Boolean? = null): Response<OkResponse>

	// Reuniones (solo lectura)
	@GET("api/midweek")
	suspend fun midweekList(@Query("from") from: String, @Query("to") to: String): Response<MidweekListResponse>

	@GET("api/midweek/{id}")
	suspend fun midweekDetail(@Path("id") id: String): Response<MidweekDetail>

	@GET("api/weekend")
	suspend fun weekendList(@Query("from") from: String, @Query("to") to: String): Response<WeekendListResponse>

	@GET("api/weekend/{id}")
	suspend fun weekendDetail(@Path("id") id: String): Response<WeekendDetail>

	@GET("api/meetings/operational-view")
	suspend fun operationalView(@Query("meetingType") type: String, @Query("meetingId") id: String): Response<OperationalViewResponse>

	// Informes S-21
	@GET("api/reports/s21/{userId}")
	suspend fun s21(@Path("userId") userId: String): Response<S21Response>

	@PUT("api/reports/s21/{userId}")
	suspend fun saveS21(@Path("userId") userId: String, @Body body: S21SaveRequest): Response<OkResponse>

	@DELETE("api/reports/s21/{userId}")
	suspend fun deleteS21(@Path("userId") userId: String, @Query("year") year: Int, @Query("month") month: Int): Response<OkResponse>

	// Publicadores
	@GET("api/publishers")
	suspend fun publishers(@Query("cursor") cursor: Int = 0, @Query("search") search: String? = null): Response<PublishersResponse>

	@GET("api/devices/config")
	suspend fun deviceConfig(): Response<DeviceConfig>

	@POST("api/devices/register")
	suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<OkResponse>

	@POST("api/devices/unregister")
	suspend fun unregisterDevice(@Body body: RegisterDeviceRequest): Response<OkResponse>

	@GET("api/preaching/calendar")
	suspend fun preachingMonth(@Query("month") month: String): Response<PreachingMonthResponse>

	@GET("api/preaching/options")
	suspend fun preachingOptions(): Response<PreachingOptions>

	@POST("api/preaching")
	suspend fun createPreaching(@Body body: CreatePreachingRequest): Response<CreatedResponse>

	@GET("api/territories")
	suspend fun territories(): Response<TerritoryListResponse>

	@POST("api/territories")
	suspend fun createTerritory(@Body body: SaveTerritoryRequest): Response<CreatedResponse>

	@PATCH("api/territories/{id}")
	suspend fun updateTerritory(@Path("id") id: String, @Body body: SaveTerritoryRequest): Response<OkResponse>

	@PATCH("api/territories/{id}")
	suspend fun saveTerritoryMap(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): Response<OkResponse>

	@DELETE("api/territories/{id}")
	suspend fun deleteTerritory(@Path("id") id: String): Response<OkResponse>

	@GET("api/territories/assignees")
	suspend fun territoryAssignees(@Query("kind") kind: String): Response<TerritoryAssigneesResponse>

	@POST("api/territories/{id}/assign")
	suspend fun assignTerritory(@Path("id") id: String, @Body body: AssignTerritoryRequest): Response<OkResponse>

	@DELETE("api/territories/{id}/assign")
	suspend fun unassignTerritory(@Path("id") id: String): Response<OkResponse>

	@POST("api/territories/{id}/notify")
	suspend fun notifyTerritory(@Path("id") id: String): Response<OkResponse>

	@GET("api/territories/assignments")
	suspend fun territoryAssignments(): Response<TerritoryAssignmentListResponse>

	@PATCH("api/territories/assignments/{id}")
	suspend fun resolveTerritoryAssignment(@Path("id") id: String, @Body body: ResolveTerritoryRequest): Response<OkResponse>

	@DELETE("api/territories/assignments/{id}")
	suspend fun deleteTerritoryAssignment(@Path("id") id: String): Response<OkResponse>

	@GET("api/attendance")
	suspend fun attendanceFrom(@Query("from") from: String): Response<AttendanceListResponse>

	@GET("api/attendance/summary")
	suspend fun attendanceSummary(@Query("from") from: String): Response<AttendanceSummaryResponse>

	@POST("api/attendance")
	suspend fun saveAttendance(@Body body: AttendanceRequest): Response<OkResponse>

	@GET("api/reports/s4/mine")
	suspend fun myReport(@Query("year") year: Int, @Query("month") month: Int): Response<MyReportResponse>

	@POST("api/reports/s4")
	suspend fun saveMyReport(@Body body: S4SaveRequest): Response<OkResponse>

	@GET("api/reports/s4")
	suspend fun s4People(): Response<S4ListResponse>

	@GET("api/publishers/{id}")
	suspend fun publisher(@Path("id") id: String): Response<PublisherItem>

}
