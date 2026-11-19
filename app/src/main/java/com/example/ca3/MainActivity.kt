
package com.example.ca3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import com.example.ca3.ui.theme.Ca3Theme

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("studentId")]
)
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val subjectName: String,
    val studentId: Int
)

data class StudentWithSubjects(
    @Embedded
    val student: Student,

    @Relation(
        parentColumn = "id",
        entityColumn = "studentId"
    )
    val subjects: List<Subject>
)

@Dao
abstract class StudentDao {

    @Insert
    abstract suspend fun insertStudent(student: Student): Long

    @Insert
    abstract suspend fun insertSubjects(subjects: List<Subject>)

    @Query("SELECT COUNT(*) FROM subjects WHERE studentId = :studentId")
    abstract suspend fun getSubjectCount(studentId: Int): Int

    @Transaction
    @Query("SELECT * FROM students")
    abstract fun getStudentsWithSubjects(): Flow<List<StudentWithSubjects>>

    @Query("DELETE FROM students WHERE id = :studentId")
    abstract suspend fun deleteStudent(studentId: Int)

    @Transaction
    open suspend fun createStudentWithSubjects(
        student: Student,
        subjectNames: List<String>
    ): Boolean {

        if (subjectNames.isEmpty() || subjectNames.size > 3) {
            return false
        }

        val studentId = insertStudent(student).toInt()

        val count = getSubjectCount(studentId)

        if (count + subjectNames.size > 3) {
            deleteStudent(studentId)
            return false
        }

        val subjects = subjectNames.map {
            Subject(
                subjectName = it,
                studentId = studentId
            )
        }

        insertSubjects(subjects)

        return true
    }

    @Transaction
    open suspend fun addSubjectsToStudent(
        studentId: Int,
        subjectNames: List<String>
    ): Boolean {

        val currentCount = getSubjectCount(studentId)

        if (currentCount + subjectNames.size > 3) {
            return false
        }

        val subjects = subjectNames.map {
            Subject(
                subjectName = it,
                studentId = studentId
            )
        }

        insertSubjects(subjects)

        return true
    }
}

@Database(
    entities = [Student::class, Subject::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "student_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.studentDao()

        setContent {
            Ca3Theme {
                StudentScreen(dao)
            }
        }
    }
}

@Composable
fun StudentScreen(dao: StudentDao) {

    var studentName by remember { mutableStateOf("") }

    var subject1 by remember { mutableStateOf("") }
    var subject2 by remember { mutableStateOf("") }
    var subject3 by remember { mutableStateOf("") }
    var subject4 by remember { mutableStateOf("") }

    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val students by dao.getStudentsWithSubjects()
        .collectAsState(initial = emptyList())

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            Text(
                text = "Student Subject Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Add Student",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = studentName,
                onValueChange = { studentName = it },
                label = { Text("Student Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Add Subjects (Maximum 3)",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = subject1,
                onValueChange = { subject1 = it },
                label = { Text("Subject 1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = subject2,
                onValueChange = { subject2 = it },
                label = { Text("Subject 2") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = subject3,
                onValueChange = { subject3 = it },
                label = { Text("Subject 3") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = subject4,
                onValueChange = { subject4 = it },
                label = { Text("Subject 4 (Limit Test)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {

                    val subjects = listOf(
                        subject1,
                        subject2,
                        subject3,
                        subject4
                    ).map { it.trim() }
                        .filter { it.isNotEmpty() }

                    if (studentName.isBlank()) {

                        message = "Please enter student name"
                        isError = true

                    } else if (subjects.isEmpty()) {

                        message = "Please enter at least one subject"
                        isError = true

                    } else {

                        scope.launch {

                            val result = dao.createStudentWithSubjects(
                                Student(name = studentName.trim()),
                                subjects
                            )

                            if (result) {

                                message = "Student added successfully!"
                                isError = false

                                studentName = ""
                                subject1 = ""
                                subject2 = ""
                                subject3 = ""
                                subject4 = ""

                            } else {

                                message = "Limit exceeded! Maximum 3 subjects allowed."
                                isError = true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Student")
            }

            if (message.isNotEmpty()) {

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    color = if (isError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Students and Subjects",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (students.isEmpty()) {

                Text(
                    text = "No students added yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            } else {

                students.forEach { item ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 3.dp
                        )
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = "${item.student.name} (ID: ${item.student.id})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Subjects (${item.subjects.size}/3)",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            item.subjects.forEach { subject ->

                                Text(
                                    text = "• ${subject.subjectName}",
                                    modifier = Modifier.padding(
                                        start = 8.dp,
                                        top = 4.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
