package eu.bolh.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import eu.bolh.app.domain.model.GoalStatus
import java.time.LocalDate

/** Цель — крупный результат со сроком: «своё жильё», «нидерландский A2» (§4.1). */
@Entity(
    tableName = "goals",
    indices = [Index("status"), Index("deletedAt")],
)
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val startDate: LocalDate,
    val deadline: LocalDate?,
    val status: GoalStatus = GoalStatus.Active,
    val doneAt: LocalDate? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Этап — обозримый кусок цели с опциональной датой. */
@Entity(
    tableName = "stages",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId"), Index("deletedAt")],
)
data class StageEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val name: String,
    val dueDate: LocalDate? = null,
    val doneAt: LocalDate? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Задача — чек-лист внутри этапа. Отдельной вкладки у задач нет (§9.1). */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id"],
            childColumns = ["stageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stageId"), Index("deletedAt")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val stageId: String,
    val name: String,
    val doneAt: LocalDate? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * История сроков — источник личного коэффициента (§4.4).
 *
 * На каждый перенос заводится своя запись, но коэффициент считается по первой:
 * от неё видно, насколько человек ошибся изначально. Если считать от последнего
 * переноса, коэффициент всегда стремился бы к единице.
 */
@Entity(
    tableName = "deadline_records",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId"), Index("deletedAt")],
)
data class DeadlineRecordEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val plannedStart: LocalDate,
    val plannedEnd: LocalDate,
    val actualEnd: LocalDate? = null,
    /** true — срок появился при переносе просроченной цели, а не при её создании. */
    val wasRescheduled: Boolean = false,
    /** Порядковый номер срока у этой цели: 0 — первый, поставленный при создании. */
    val sequence: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Статья сметы — из чего складывается цена цели (§4.5).
 *
 * «Переезд стоит 3000» не говорит ничего о том, за что именно платить и что
 * уже закрыто. «Бухгалтеру 500, налоги 900, грузчики 400» говорит: видно и
 * общую сумму, и что осталось.
 *
 * Валюты у статьи своей нет: смета живёт в валюте копилки цели, а без курсов
 * складывать разные валюты в один итог всё равно нельзя.
 */
@Entity(
    tableName = "goal_cost_items",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId"), Index("deletedAt")],
)
data class GoalCostItemEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val name: String,
    val plannedMinor: Long,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
