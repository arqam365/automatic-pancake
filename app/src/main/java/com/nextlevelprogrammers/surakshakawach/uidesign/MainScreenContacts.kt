package com.nextlevelprogrammers.surakshakawach.uidesign

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nextlevelprogrammers.surakshakawach.IntentAction.ContactScreenAction
import com.nextlevelprogrammers.surakshakawach.ViewModels.ContactScreenStateValues
import com.nextlevelprogrammers.surakshakawach.ViewModels.ContactScreenViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreenContactRoot(modifier: Modifier){
    val viewModel: ContactScreenViewModel= viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContactScreen(modifier=modifier,state=state,onAction= viewModel::onAction)
}

@Composable
fun ContactScreen(modifier: Modifier, state:ContactScreenStateValues,onAction:(ContactScreenAction)->Unit) {

    Scaffold(floatingActionButton = {
        FloatingActionButton(
            onClick = {onAction(ContactScreenAction.OnClickAddContact)},

        ){
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }) {innerPadding->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = modifier.fillMaxSize()) {
                Text(
                    text = "Emergency Contacts",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = modifier.padding(bottom = 8.dp, start = 20.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.contactList, key = { it.number }) { contact ->
                        SwipeContainer(
                            item = contact,
                            onAction=onAction
                        ) {
                            ContactCard(contact = contact)
                        }
                    }
                }
            }


            // Show edit dialog if needed
            state.currentContact?.let { contact ->
                if (state.showEditDialog) {
                    EditContactDialog(
                        contact = contact,
                        onAction =onAction
                    )
                }
            }
            //Add Contact Dialog Box
            if(state.showAddDialog){
               AddContact(state.showAddDialog, onAction)
            }
        }
    }


}

@Composable
fun AddContact(showAddDialog:Boolean, onAction: (ContactScreenAction) -> Unit)
{

    var name by remember{ mutableStateOf("")}
    var number by remember{ mutableStateOf("")}
    var email by remember{ mutableStateOf("")}
    val context= LocalContext.current
    if(showAddDialog){
        AlertDialog(
            onDismissRequest = { onAction(ContactScreenAction.OnCancelSaveContact) },
            title = { Text("Add Contact") },
            text = {
                    Column(modifier = Modifier.padding(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Contact Name") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it },
                            label = { Text("Contact Number") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email=it},
                            label = {
                                Text("Contact Email")
                            }
                        )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if(number==""|| number.length!=10 || name==""){
                        Toast.makeText(context,"Invalid Contact", Toast.LENGTH_SHORT).show()
                    }else {
                        onAction(ContactScreenAction.OnClickSaveContact(ContactInfo(name, number,email)))
                    }
                }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(ContactScreenAction.OnCancelSaveContact) },
                ){
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun ContactCard(modifier: Modifier = Modifier, contact: ContactInfo) {
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(modifier=modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
        .clip(RoundedCornerShape(4.dp))
        ) {
            Text(text = contact.name, modifier.padding(start = 6.dp))
            Text(text = contact.number, modifier.padding(start = 6.dp))
            Text(text = contact.email, modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SwipeContainer(
    item: ContactInfo,
    onAction: (ContactScreenAction) -> Unit,
    animationDuration: Int = 500,
    content: @Composable (ContactInfo) -> Unit
) {
    var isRemoved by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onAction(ContactScreenAction.OnSwipeContactEdit(item))
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    isRemoved = true
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    LaunchedEffect(key1 = isRemoved) {
        if (isRemoved) {
            delay(animationDuration.toLong())
            onAction(ContactScreenAction.OnSwipeContactDelete(item))
        }
    }

    AnimatedVisibility(
        visible = !isRemoved,
        exit = shrinkVertically(animationSpec = tween(durationMillis = animationDuration)) + fadeOut()
    ) {
        SwipeToDismissBox(
            state = state,
            backgroundContent = { DeleteBackground(swipeDismissState = state) },
            content = { content(item) }
        )
    }
}

@Composable
fun EditContactDialog(
    contact: ContactInfo,
    onAction: (ContactScreenAction) -> Unit
) {
    var newName by remember { mutableStateOf(contact.name) }
    var newNumber by remember { mutableStateOf(contact.number) }
    var newEmail by remember{ mutableStateOf(contact.email)}
    val context= LocalContext.current
    AlertDialog(
        onDismissRequest = { onAction(ContactScreenAction.OnClickEditCancel) },
        title = { Text("Edit Contact") },
        text = {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Contact Name") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newNumber,
                    onValueChange = { newNumber = it },
                    label = { Text("Contact Number") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("Contact Number") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if(newNumber==""|| newNumber.length!=10 || newName==""){
                    Toast.makeText(context,"Invalid Contact", Toast.LENGTH_SHORT).show()
                }else {
                    onAction(ContactScreenAction.OnClickEditSave(contact, newName, newNumber, newEmail))
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ContactScreenAction.OnClickEditCancel) }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteBackground(swipeDismissState: SwipeToDismissBoxState) {
    val color = when (swipeDismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Color(0xFF1DE9B6)
        SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF1744)
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit",
            tint = Color.White
        )
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color.White
        )
    }
}

