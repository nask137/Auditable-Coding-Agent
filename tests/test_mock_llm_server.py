import importlib.util
from pathlib import Path
import unittest


def load_mock_server():
    module_path = Path(__file__).resolve().parents[1] / "tools" / "mock_llm_server.py"
    spec = importlib.util.spec_from_file_location("mock_llm_server", module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class MockLlmServerTests(unittest.TestCase):
    def test_user_input_prompt_with_recovery_notes_is_not_runtime_reject(self):
        server = load_mock_server()
        prompt = """
        Return json with this exact shape.
        User request:
        force runtime to ask for user input

        Runtime recovery notes:
        []
        """

        self.assertEqual(server.scenario(prompt), "user_input")

    def test_generic_recovery_notes_heading_does_not_match_runtime_reject(self):
        server = load_mock_server()
        prompt = """
        Return json with this exact shape.
        User request:
        create a note

        Runtime recovery notes:
        []
        """

        self.assertEqual(server.scenario(prompt), "default")


if __name__ == "__main__":
    unittest.main()
