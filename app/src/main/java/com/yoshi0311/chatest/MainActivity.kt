package com.yoshi0311.chatest

import android.R.attr.x
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.yoshi0311.chatest.ui.theme.ChaTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        var x = getPythonSum()
        setContent {
            ChaTestTheme {
                Column() {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Greeting(
                            name = x.toString(),
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }

    }
}

fun getPythonSum(): Int {
    val py = Python.getInstance()
    // script.py 파일을 가져옵니다.
    val pyObj = py.getModule("script")

    // sum(1, 1) 함수를 호출하고 결과를 정수형으로 변환합니다.
    return pyObj.callAttr("sum", 1, 1).toInt()
}

@Composable
fun PythonRunnerScreen() {
    val context = LocalContext.current

    // 입력 폼의 상태 (기본값으로 sum 함수 예시를 넣어둠)
    var codeInput by remember {
        mutableStateOf("def sum(a, b):\n    return a + b")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Python Code Executor",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 파이썬 코드 입력창
        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text("Python Code") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            placeholder = { Text("여기에 파이썬 코드를 입력하세요") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 실행 버튼
        Button(
            onClick = {
                try {
                    val py = Python.getInstance()
                    // 1. 네임스페이스 생성 (파이썬의 dict 객체)
                    val globals = py.getBuiltins().callAttr("dict")

                    // 2. 입력받은 코드를 실행
                    py.getBuiltins().callAttr("exec", codeInput, globals)

                    // 3. globals dict에서 "sum"이라는 키에 해당하는 객체를 꺼냅니다.
                    // getScalar 대신 간단하게 .get("이름")을 사용하면 됩니다.
                    val sumFunc = globals.get("sum")

                    if (sumFunc != null) {
                        // 4. sum(1, 1) 실행
                        val result = sumFunc.call(1, 1)
                        Toast.makeText(context, "결과: $result", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "코드 내에 'sum' 함수를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "파이썬 에러: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run sum(1, 1)")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ChaTestTheme {
        Greeting("Android")
    }
}