def run_user_code(code_str, state_str):
    ns = {}
    try:
        exec(compile(code_str, '<bot>', 'exec'), ns)
    except Exception as e:
        raise RuntimeError("compile error: " + str(e))
    if 'move' not in ns:
        raise RuntimeError("move function not defined")
    result = ns['move'](state_str)
    if result is None:
        raise RuntimeError("move returned None")
    return str(result)


def run_user_file(file_path, state_str):
    import importlib.util
    spec = importlib.util.spec_from_file_location("user_bot", file_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if not hasattr(module, 'move'):
        raise RuntimeError("move function not defined in file")
    result = module.move(state_str)
    if result is None:
        raise RuntimeError("move returned None")
    return str(result)
