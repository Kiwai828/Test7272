import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'theme.dart';
import 'api_client.dart';
import 'login_screen.dart';
import 'shell.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Api().init(); // Init persistent cookies
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarColor: C.bg1,
    systemNavigationBarIconBrightness: Brightness.light,
  ));

  // Check if already logged in
  final loggedIn = await Api().isLoggedIn();

  runApp(ProviderScope(child: App(loggedIn: loggedIn)));
}

class App extends StatelessWidget {
  final bool loggedIn;
  const App({super.key, required this.loggedIn});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Recap Maker',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.theme,
      home: loggedIn ? const Shell() : const LoginScreen(),
    );
  }
}
