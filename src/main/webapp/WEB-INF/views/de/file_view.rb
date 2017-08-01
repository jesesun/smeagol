module Precious
  module Views
    class FileView < Layout
      attr_reader :results, :ref

      def title
        "Dateiansicht von #{@ref}"
      end

      def has_results
        !@results.empty?
      end

      def no_results
        @results.empty?
      end
    end
  end
end
